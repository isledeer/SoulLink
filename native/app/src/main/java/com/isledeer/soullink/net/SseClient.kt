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
    data object Done : SseEvent
}

/** 一轮上下文；imageBase64 非空时按多模态 content 数组发送 */
data class ChatTurn(
    val role: String,
    val content: String,
    val imageBase64: String? = null,
)

data class ChatRequest(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val turns: List<ChatTurn>,
    val systemPrompt: String = "",
    val maxTokens: Int = 2048,
    val temperature: Double = 0.8,
    /** 请求 usage 统计（部分网关不认识该字段，VM 侧会降级重试） */
    val includeUsage: Boolean = true,
)

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
                }
            }

            try {
                call.execute().use { response ->
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

    private fun buildRequest(req: ChatRequest): Request {
        val messages = JSONArray()

        if (req.systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", req.systemPrompt))
        }

        req.turns.forEach { turn ->
            val message = JSONObject().put("role", turn.role)
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
            .put("stream", true)
            .put("max_tokens", req.maxTokens)
            .put("temperature", req.temperature)

        if (req.includeUsage) {
            body.put("stream_options", JSONObject().put("include_usage", true))
        }

        return Request.Builder()
            .url(chatCompletionsUrl(req.baseUrl))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Authorization", "Bearer ${req.apiKey}")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
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

        /** 连通性测试用：GET /v1/models */
        fun modelsUrl(baseUrl: String): String {
            var clean = baseUrl.trim().trimEnd('/')
            if (clean.endsWith("/v1")) clean = clean.dropLast(3)
            return "$clean/v1/models"
        }
    }
}