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
 *   setTitle(title)                         更新任务卡片标题（无原生顶栏）
 *   getPlatformInfo()                       返回平台信息 JSON
 *   pingJs()                                桥自检：Kotlin 反向调 window.SoulLink.onNativePing
 *   sendChatRequest(id, url, headers, body) 发起原生 SSE 请求（NativeSSE）
 *   abortChatRequest(id)                    中断指定请求
 *
 * Kotlin → JS（evaluateJavascript 调 window.SoulLink.xxx，内核侧注册）：
 *   receiveChunk(id, {type, text})         流式增量（type: content|reasoning）
 *   receiveDone(id)                        流结束
 *   receiveError(id, message)              失败
 *   onNativePing(msg)                      外壳主动 ping（自检用）
 *
 * 注意：@JavascriptInterface 方法跑在 JavaBridge 线程，触碰 Compose
 * 状态必须 post 回主线程（setTitle 就这么做）。
 */
@Deprecated("WebView 时代遗留，世界书已改用原生 Compose 页面，此类仅留档。")
class WebAppInterface(
    private val webView: WebView,
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

    /**
     * 桥自检入口（原「PING JS」按钮已随原生顶栏移除）。
     * Web 侧调试面板里加一行 window.SoulLinkNative.pingJs() 即可验证反向通路。
     */
    @JavascriptInterface
    fun pingJs() {
        webView.post {
            webView.evaluateJavascript(
                "window.SoulLink && window.SoulLink.onNativePing " +
                    "? window.SoulLink.onNativePing('hello from kotlin') : 'no handler'",
                null
            )
        }
    }

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