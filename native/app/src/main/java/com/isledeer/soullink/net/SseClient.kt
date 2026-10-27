package com.isledeer.soullink.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface SseEvent {
    data class Content(val text: String) : SseEvent
    data class Reasoning(val text: String) : SseEvent
    data class Usage(val promptTokens: Int, val completionTokens: Int) : SseEvent
    data class ToolCall(val id: String, val name: String, val arguments: String) : SseEvent
    data object Done : SseEvent
}

/** 一轮上下文；imageBase64 非空时按多模态 content 数组发送 */
data class ChatTurn(
    val role: String,
    val content: String,
    val imageBase64: String? = null,
    /** role == "tool" 时携带，回填给 API */
    val toolCallId: String? = null,
)

data class ChatRequest(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val turns: List<ChatTurn>,
    val systemPrompt: String = "",
    val maxTokens: Int = 2048,
    val temperature: Double = 0.8,
    val topP: Double = 1.0,
    /** 助手启用的工具 schema（已组装成 {"type":"function","function":{...}}） */
    val toolSchemas: List<JSONObject> = emptyList(),
    /** 助手自定义请求头 / 请求体字段 */
    val extraHeaders: Map<String, String> = emptyMap(),
    val extraBody: Map<String, String> = emptyMap(),
    /** 请求 usage 统计（部分网关不认识该字段，VM 侧会降级重试） */
    val includeUsage: Boolean = true,
    /** 思考深度：OFF / LOW / MEDIUM / HIGH / XHIGH（非 OFF 时下发 reasoning_effort） */
    val thinking: String = "OFF",
)

private class ToolFragment {
    var id: String = ""
    var name: String = ""
    val args = StringBuilder()
}

/** 请求日志环形缓冲（设置页「请求日志」与 RAW 调试面板共用）。 */
object RequestLog {
    private const val MAX = 200
    private val entries = java.util.Collections.synchronizedList(mutableListOf<String>())

    fun add(line: String) {
        entries.add(0, line)
        while (entries.size > MAX) entries.removeAt(entries.size - 1)
    }

    fun snapshot(): List<String> = synchronized(entries) { entries.toList() }
}

/**
 * 原生 SSE 客户端。callbackFlow 保证可取消：collector 取消 → awaitClose 里 call.cancel()
 * → 阻塞读立刻退出，这就是「停止」按钮的实现。
 *
 * 断帧：readUtf8Line 会剥掉行尾 \r，因此 \n\n 与 \r\n\r\n 都能正确切分。
 */
class SseClient(
    private val client: OkHttpClient = defaultClient()
) {

    fun stream(request: ChatRequest): Flow<SseEvent> = callbackFlow {
        val call = client.newCall(buildRequest(request))

        val reader = launch(Dispatchers.IO) {
            // tool_calls 参数跨 chunk 累积（按 index 分片）
            val toolFragments = mutableMapOf<Int, ToolFragment>()

            fun emitFromEvent(lines: List<String>) {
                for (raw in lines) {
                    val line = raw.trim()
                    if (!line.startsWith("data:")) continue

                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue

                    val obj = runCatching { JSONObject(payload) }.getOrNull() ?: continue

                    obj.optJSONObject("usage")?.let { usage ->
                        trySend(
                            SseEvent.Usage(
                                promptTokens = usage.optInt("prompt_tokens"),
                                completionTokens = usage.optInt("completion_tokens"),
                            )
                        )
                    }

                    val delta = obj.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?: continue

                    val reasoning = delta.optString("reasoning_content")
                        .ifEmpty { delta.optString("reasoning") }
                        .ifEmpty { delta.optString("thinking") }
                    val content = delta.optString("content")

                    if (reasoning.isNotEmpty()) trySend(SseEvent.Reasoning(reasoning))
                    if (content.isNotEmpty()) trySend(SseEvent.Content(content))

                    // 流式 tool_calls：arguments 可能分片，按 index 累积
                    val toolCalls = delta.optJSONArray("tool_calls") ?: continue
                    for (ti in 0 until toolCalls.length()) {
                        val call = toolCalls.optJSONObject(ti) ?: continue
                        val index = call.optInt("index", 0)
                        val fragment = toolFragments.getOrPut(index) { ToolFragment() }
                        call.optString("id").takeIf { it.isNotBlank() }?.let { fragment.id = it }
                        call.optJSONObject("function")?.let { fn ->
                            fn.optString("name").takeIf { it.isNotBlank() }?.let { fragment.name = it }
                            fn.optString("arguments").takeIf { it.isNotBlank() }?.let { fragment.args.append(it) }
                        }
                        // 参数凑成完整 JSON 才发出一次调用
                        if (fragment.id.isNotBlank() && fragment.name.isNotBlank() && fragment.args.isNotBlank()) {
                            val complete = runCatching { JSONObject(fragment.args.toString()) }.isSuccess
                            if (complete) {
                                trySend(SseEvent.ToolCall(fragment.id, fragment.name, fragment.args.toString()))
                                toolFragments.remove(index)
                            }
                        }
                    }
                }
            }

            try {
                call.execute().use { response ->
                    val code = response.code
                    RequestLog.add(
                        "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())} " +
                            "POST /v1/chat/completions -> $code"
                    )
                    if (!response.isSuccessful) {
                        val snippet = runCatching { response.body?.string()?.take(300) }
                            .getOrNull().orEmpty()
                        throw IOException("HTTP ${response.code} $snippet")
                    }

                    val source = response.body?.source() ?: throw IOException("响应体为空")

                    var buffer = mutableListOf<String>()
                    while (isActive) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) {
                            if (buffer.isNotEmpty()) {
                                emitFromEvent(buffer)
                                buffer = mutableListOf()
                            }
                        } else {
                            buffer.add(line)
                        }
                    }
                    if (buffer.isNotEmpty()) emitFromEvent(buffer)

                    trySend(SseEvent.Done)
                    close()
                }
            } catch (e: CancellationException) {
                close()
            } catch (e: Exception) {
                close(e)
            }
        }

        awaitClose {
            call.cancel()
            reader.cancel()
        }
    }

    /** 通用 GET（余额等查询）：返回原始 JSON 文本 */
    suspend fun fetchJson(url: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
    }

    /** DALL·E 风格生图：POST /v1/images/generations（b64_json），返回 base64 */
    suspend fun generateImage(baseUrl: String, apiKey: String, prompt: String, model: String = "dall-e-3"): String? =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", model)
                .put("prompt", prompt)
                .put("n", 1)
                .put("size", "1024x1024")
                .put("response_format", "b64_json")

            val request = Request.Builder()
                .url(imagesUrl(baseUrl))
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val root = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                    ?: return@withContext null
                root.optJSONArray("data")?.optJSONObject(0)?.optString("b64_json")
                    ?.takeIf { it.isNotBlank() }
            }
        }

    /** 拉取该 provider 的模型列表（/v1/models → data[].id） */
    suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(modelsUrl(baseUrl))
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val snippet = runCatching { response.body?.string()?.take(200) }.getOrNull().orEmpty()
                RequestLog.add("GET ${modelsUrl(baseUrl)} -> ${response.code}")
                throw IOException("HTTP ${response.code} $snippet")
            }
            val body = response.body?.string().orEmpty()
            val root = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext emptyList()
            val array = root.optJSONArray("data") ?: return@withContext emptyList()
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
            }
        }
    }

    /** 连通性测试：GET /v1/models，返回可读结果或抛异常 */
    suspend fun testConnection(baseUrl: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(modelsUrl(baseUrl))
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val snippet = runCatching { response.body?.string()?.take(200) }.getOrNull().orEmpty()
                throw IOException("HTTP ${response.code} $snippet")
            }
            val body = response.body?.string().orEmpty()
            val count = runCatching {
                JSONObject(body).optJSONArray("data")?.length() ?: 0
            }.getOrDefault(0)
            if (count > 0) "连接成功 · $count 个模型" else "连接成功"
        }
    }

    private fun buildBody(req: ChatRequest, stream: Boolean): JSONObject {
        val messages = JSONArray()

        if (req.systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", req.systemPrompt))
        }

        req.turns.forEach { turn ->
            val message = JSONObject().put("role", turn.role)
            if (turn.role == "tool" && !turn.toolCallId.isNullOrBlank()) {
                message.put("tool_call_id", turn.toolCallId)
            }
            val image = turn.imageBase64
            if (image.isNullOrBlank()) {
                message.put("content", turn.content)
            } else {
                // OpenAI 多模态格式：content 为 parts 数组
                val parts = JSONArray()
                if (turn.content.isNotBlank()) {
                    parts.put(JSONObject().put("type", "text").put("text", turn.content))
                }
                parts.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put(
                            "image_url",
                            JSONObject().put("url", "data:image/jpeg;base64,$image")
                        )
                )
                message.put("content", parts)
            }
            messages.put(message)
        }

        val body = JSONObject()
            .put("model", req.model)
            .put("messages", messages)
            .put("stream", stream)
            .put("max_tokens", req.maxTokens)
            .put("temperature", req.temperature)
            .put("top_p", req.topP)

        if (stream && req.includeUsage) {
            body.put("stream_options", JSONObject().put("include_usage", true))
        }

        // 思考深度：多数网关认 reasoning_effort（low/medium/high），原样下发
        if (req.thinking != "OFF") {
            body.put("reasoning_effort", req.thinking.lowercase())
        }

        if (req.toolSchemas.isNotEmpty()) {
            val toolArray = JSONArray()
            req.toolSchemas.forEach { toolArray.put(it) }
            body.put("tools", toolArray)
        }

        // 助手自定义请求体：能解析成 JSON 的按 JSON 放，否则当字符串
        req.extraBody.forEach { (key, value) ->
            if (value.isBlank()) return@forEach
            val parsed = runCatching { JSONObject(value) }.getOrNull()
                ?: runCatching { JSONArray(value) }.getOrNull()
            body.put(key, parsed ?: value)
        }

        return body
    }

    private fun buildRequest(req: ChatRequest): Request {
        val builder = Request.Builder()
            .url(chatCompletionsUrl(req.baseUrl))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Authorization", "Bearer ${req.apiKey}")

        req.extraHeaders.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) builder.addHeader(key, value)
        }

        return builder
            .post(buildBody(req, true).toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    /** 非流式补全：一次性返回正文（记忆摘要 / 世界书 AI 填充用）。 */
    suspend fun complete(request: ChatRequest): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(chatCompletionsUrl(request.baseUrl))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${request.apiKey}")
            .post(buildBody(request, false).toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val started = System.currentTimeMillis()
        client.newCall(req).execute().use { response ->
            val code = response.code
            RequestLog.add(
                "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())} " +
                    "POST /v1/chat/completions(non-stream) -> $code (${System.currentTimeMillis() - started}ms)"
            )
            if (!response.isSuccessful) {
                val snippet = runCatching { response.body?.string()?.take(200) }.getOrNull().orEmpty()
                throw IOException("HTTP $code $snippet")
            }
            val root = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if (root == null) {
                ""
            } else {
                root.optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content").orEmpty()
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .build()

        /** 去尾斜杠、去尾 /v1，统一拼 /v1/chat/completions */
        fun chatCompletionsUrl(baseUrl: String): String {
            var clean = baseUrl.trim().trimEnd('/')
            if (clean.endsWith("/v1")) clean = clean.dropLast(3)
            return "$clean/v1/chat/completions"
        }

        /** 生图端点 */
        fun imagesUrl(baseUrl: String): String {
            var clean = baseUrl.trim().trimEnd('/')
            if (clean.endsWith("/v1")) clean = clean.dropLast(3)
            return "$clean/v1/images/generations"
        }

        /** 连通性测试用：GET /v1/models */
        fun modelsUrl(baseUrl: String): String {
            var clean = baseUrl.trim().trimEnd('/')
            if (clean.endsWith("/v1")) clean = clean.dropLast(3)
            return "$clean/v1/models"
        }
    }
}