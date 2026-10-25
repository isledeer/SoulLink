package com.isledeer.soullink

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 原生 SSE 层（OkHttp）。
 *
 * 为什么要它：React 里的 fetch + ReadableStream 在 WebView 中有两个短板——
 * 受 CORS 限制（origin 不是 null 了，但仍是浏览器语义），以及每个 chunk 都要
 * 穿过 JS 桥/事件循环。放到 Kotlin 侧收流后，只把解析好的增量推给 React，
 * 流式更稳，也顺手绕开 CORS。
 *
 * 协议与 src/services/apiClient.ts 的 parseSSEEvent 保持**逐字段对齐**，
 * 便于后续把 React 的请求通道整体换过来而不改消费逻辑：
 *   - 事件按空行分隔；readUtf8Line 天然同时处理 \n 与 \r\n
 *     （顺带修掉旧实现按 '\n\n' 硬切导致 CRLF 网关整段解析失败的隐患）
 *   - "data: [DONE]" 结束
 *   - delta.reasoning_content / reasoning / thinking → reasoning chunk
 *   - delta.content → content chunk
 *
 * 本阶段它只是"可用通道"，React 仍走自己的 fetch（第一步不强行接管）；
 * 第二阶段把 apiClient 换成桥调用即可。
 */
class NativeSSE(private val webView: WebView) {

    private val main = Handler(Looper.getMainLooper())

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // 流式生成可能很久，只限制建连与首字节前的空闲，不掐总时长
        .readTimeout(10, TimeUnit.MINUTES)
        .build()

    private val calls = ConcurrentHashMap<String, Call>()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun start(requestId: String, url: String, headersJson: String, bodyJson: String) {
        val builder = Request.Builder().url(url).post(bodyJson.toRequestBody(jsonMediaType))

        runCatching { JSONObject(headersJson) }.onSuccess { headers ->
            val keys = headers.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                builder.header(key, headers.getString(key))
            }
        }

        val call = client.newCall(builder.build())
        calls[requestId] = call

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                calls.remove(requestId)
                if (call.isCanceled()) return // 用户主动中断，不算错误
                emitError(requestId, "网络请求失败: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                calls.remove(requestId)

                if (!response.isSuccessful) {
                    val snippet = runCatching {
                        response.body?.string()?.take(200)
                    }.getOrNull() ?: ""
                    response.close()
                    emitError(requestId, "HTTP ${response.code}: $snippet")
                    return
                }

                val body = response.body
                if (body == null) {
                    response.close()
                    emitError(requestId, "HTTP ${response.code}: 空响应体")
                    return
                }

                try {
                    val source = body.source()
                    var event = mutableListOf<String>()

                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) {
                            if (event.isNotEmpty()) {
                                dispatch(requestId, event)
                                event = mutableListOf()
                            }
                        } else {
                            event.add(line)
                        }
                    }
                    if (event.isNotEmpty()) dispatch(requestId, event)

                    emitDone(requestId)
                } catch (e: Exception) {
                    if (!call.isCanceled()) {
                        emitError(requestId, "流解析中断: ${e.message}")
                    }
                } finally {
                    response.close()
                }
            }
        })
    }

    /** 中断指定请求；React 的 abort() 对应这里 */
    fun abort(requestId: String) {
        calls.remove(requestId)?.cancel()
    }

    // ---------- SSE 解析 ----------

    private fun dispatch(requestId: String, lines: List<String>) {
        for (raw in lines) {
            val line = raw.trim()
            if (!line.startsWith("data:")) continue

            val data = line.removePrefix("data:").trim()
            if (data.isEmpty() || data == "[DONE]") continue

            val parsed = runCatching { JSONObject(data) }.getOrNull()
            if (parsed == null) continue

            val delta = parsed.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("delta")
            if (delta == null) continue

            val reasoning = delta.optString("reasoning_content")
                .ifEmpty { delta.optString("reasoning") }
                .ifEmpty { delta.optString("thinking") }
            val content = delta.optString("content")

            if (reasoning.isNotEmpty()) emitChunk(requestId, "reasoning", reasoning)
            if (content.isNotEmpty()) emitChunk(requestId, "content", content)
        }
    }

    // ---------- 推给 JS ----------

    private fun emitChunk(requestId: String, type: String, text: String) {
        val payload = JSONObject().put("type", type).put("text", text).toString()
        evalJs(
            "window.SoulLink && window.SoulLink.receiveChunk && " +
                "window.SoulLink.receiveChunk(${JSONObject.quote(requestId)}, $payload)"
        )
    }

    private fun emitDone(requestId: String) {
        evalJs(
            "window.SoulLink && window.SoulLink.receiveDone && " +
                "window.SoulLink.receiveDone(${JSONObject.quote(requestId)})"
        )
    }

    private fun emitError(requestId: String, message: String) {
        Log.e(TAG, "[$requestId] $message")
        evalJs(
            "window.SoulLink && window.SoulLink.receiveError && " +
                "window.SoulLink.receiveError(${JSONObject.quote(requestId)}, " +
                "${JSONObject.quote(message)})"
        )
    }

    /** evaluateJavascript 必须在 UI 线程调 */
    private fun evalJs(js: String) {
        main.post {
            try {
                webView.evaluateJavascript(js, null)
            } catch (e: Exception) {
                Log.d(TAG, "evaluateJavascript 失败: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "SoulLinkSSE"
    }
}