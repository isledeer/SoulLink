package com.isledeer.soullink

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * SoulLink 混合架构主入口：Compose 原生外壳 + WebView React 内核。
 *
 * 内核加载走本地 HTTP Server（LocalAssetServer）而非 file:///android_asset，
 * 原因见 LocalAssetServer 的类注释。外壳目前只提供：
 *   - TopAppBar（标题由 JS 经桥推送，演示 JS→Kotlin 通路）
 *   - PING JS 按钮（演示 Kotlin→JS 通路）
 *   - 返回键接管（hash 路由回退 / 退出）
 *   - <input type="file"> 文件选择器桥接（角色卡导入依赖它，漏了导入直接死）
 *
 * 后续设置页、导航栏等原生 UI 都往这个 Compose 骨架里加。
 */
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var title by mutableStateOf("SoulLink")
    private var server: LocalAssetServer? = null

    /** <input type="file"> 的回传目标（角色卡 PNG 导入走这里） */
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val value = if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { arrayOf(it) }
                    ?: WebChromeClient.FileChooserParams.parseResult(
                        result.resultCode, result.data
                    )
            } else {
                null
            }
            filePathCallback?.onReceiveValue(value)
            filePathCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 调试期允许 chrome://inspect 远程调试 WebView（release 自动关闭）
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // 先起本地内核服务器（同步 bind，极快），再进 Compose 挂 WebView
        server = LocalAssetServer(this).also { it.start() }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopAppBar(
                            title = { Text(title) },
                            actions = {
                                TextButton(onClick = { pingJs() }) {
                                    Text("PING JS")
                                }
                            }
                        )
                        AndroidView(
                            factory = { ctx -> createWebView(ctx) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            BackHandler {
                if (this::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(ctx: Context): WebView {
        return WebView(ctx).apply {
            this@MainActivity.webView = this

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // 内核走 http://127.0.0.1，不需要 file 访问，收紧权限
            settings.allowFileAccess = false
            settings.allowContentAccess = false

            // 桥：JS 调 window.SoulLinkNative.*，Kotlin 反向走 evaluateJavascript
            addJavascriptInterface(
                WebAppInterface(this) { newTitle -> title = newTitle },
                JS_BRIDGE_NAME
            )

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    // 一切导航都留在本 WebView 内，不跳系统浏览器
                    view.loadUrl(request.url.toString())
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                    Log.d(TAG, "[web:${msg.messageLevel()}] ${msg.message()}")
                    return true
                }

                override fun onShowFileChooser(
                    view: WebView,
                    callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams
                ): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = callback
                    return try {
                        filePicker.launch(params.createIntent())
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "文件选择器启动失败", e)
                        callback.onReceiveValue(null)
                        filePathCallback = null
                        false
                    }
                }
            }

            loadUrl(server?.entryUrl() ?: FALLBACK_URL)
        }
    }

    /** Kotlin → JS 演示：调内核里注册的 window.SoulLink.onNativePing */
    private fun pingJs() {
        if (!this::webView.isInitialized) return
        webView.evaluateJavascript(
            "window.SoulLink && window.SoulLink.onNativePing " +
                "? window.SoulLink.onNativePing('hello from kotlin') : 'no handler'",
            null
        )
    }

    override fun onPause() {
        super.onPause()
        if (this::webView.isInitialized) webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (this::webView.isInitialized) webView.onResume()
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        if (this::webView.isInitialized) {
            webView.removeJavascriptInterface(JS_BRIDGE_NAME)
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SoulLinkMain"
        const val JS_BRIDGE_NAME = "SoulLinkNative"
        private const val FALLBACK_URL = "file:///android_asset/index.html"
    }
}