package com.isledeer.soullink

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/**
 * JS ↔ Kotlin 通信桥。
 *
 * JS → Kotlin（React 侧直接调 window.SoulLinkNative.xxx）：
 *   setTitle(title)                         更新 Compose 顶栏标题
 *   getPlatformInfo()                       返回平台信息 JSON
 *   sendChatRequest(id, url, headers, body) 发起原生 SSE 请求（NativeSSE）
 *   abortChatRequest(id)                    中断指定请求
 *
 * Kotlin → JS（evaluateJavascript 调 window.SoulLink.xxx，内核侧注册）：
 *   receiveChunk(id, {type, text})         流式增量（type: content|reasoning）
 *   receiveDone(id)                        流结束
 *   receiveError(id, message)              失败
 *   onNativePing(msg)                      外壳主动 ping（演示用）
 *
 * 注意：@JavascriptInterface 方法跑在 JavaBridge 线程，触碰 Compose
 * 状态必须 post 回主线程（setTitle 就这么做）。
 */
class WebAppInterface(
    webView: WebView,
    private val onTitle: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val sse = NativeSSE(webView)

    @JavascriptInterface
    fun setTitle(title: String) {
        main.post { onTitle(title) }
    }

    @JavascriptInterface
    fun getPlatformInfo(): String =
        JSONObject()
            .put("platform", "android-native")
            .put("bridgeVersion", 1)
            .toString()

    @JavascriptInterface
    fun sendChatRequest(
        requestId: String,
        url: String,
        headersJson: String,
        bodyJson: String
    ) {
        sse.start(requestId, url, headersJson, bodyJson)
    }

    @JavascriptInterface
    fun abortChatRequest(requestId: String) {
        sse.abort(requestId)
    }
}