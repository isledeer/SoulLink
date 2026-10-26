package com.isledeer.soullink.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

/**
 * 流式事件。与 ui 层的 ChatMessage 解耦：网络层只吐「增量」，不关心怎么渲染。
 */
sealed interface SseEvent {
    data class Content(val text: String) : SseEvent
    data class Reasoning(val text: String) : SseEvent
    data object Done : SseEvent
}

data class ChatTurn(val role: String, val content: String)

data class ChatRequest(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val turns: List<ChatTurn>,
    val maxTokens: Int = 2048,
    val temperature: Double = 0.8,
)

/**
 * 原生 SSE 客户端（纯 Kotlin，无 WebView）。
 *
 * 由 NativeSSE.kt 演进而来：那边靠 evaluateJavascript 往 WebView 推数据，
 * 现在改为 callbackFlow —— 收集方拿到的是 Flow<SseEvent>，天然可取消：
 * 用户点「停止」→ 协程取消 → awaitClose 里 call.cancel() → 阻塞读立刻抛异常退出。
 *
 * 协议对齐 OpenAI Chat Completions：
 *   data: {"choices":[{"delta":{"content":"…"}}]}      → Content
 *   data: {"choices":[{"delta":{"reasoning_content":"…"}}]} → Reasoning
 *   data: [DONE]                                        → Done
 *   delta.reasoning / delta.thinking 也认（兼容不同厂商）
 *
 * readUtf8Line 会自动剥掉行尾 \r，所以 \n\n 与 \r\n\r\n 两种分隔都能正确断帧
 * ——这是老 Web 版按 '\n\n' 硬切留下的坑，这里顺手根治。
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
                // 用户主动停止：不当作错误
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

    private fun buildRequest(req: ChatRequest): Request {
        val messages = JSONArray()
        req.turns.forEach { turn ->
            messages.put(
                JSONObject()
                    .put("role", turn.role)
                    .put("content", turn.content)
            )
        }

        val body = JSONObject()
            .put("model", req.model)
            .put("messages", messages)
            .put("stream", true)
            .put("max_tokens", req.maxTokens)
            .put("temperature", req.temperature)

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
            // 流式生成可能很久，只兜底「长时间无数据」
            .readTimeout(10, TimeUnit.MINUTES)
            .build()

        /**
         * baseUrl 归一化：去尾部斜杠、去尾部 /v1，再统一拼 /v1/chat/completions。
         * 用户填 https://api.openai.com、…/v1、…/v1/ 三种写法都能通。
         */
        fun chatCompletionsUrl(baseUrl: String): String {
            var clean = baseUrl.trim().trimEnd('/')
            if (clean.endsWith("/v1")) clean = clean.dropLast(3)
            return "$clean/v1/chat/completions"
        }
    }
}