package com.isledeer.soullink

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat

/**
 * 混合架构主入口：**不设原生顶栏**，WebView 全屏承载 React 内核。
 *
 * 设计取舍（v2）：
 *  - 删掉 Compose TopAppBar：Web 内核自带导航栏，双层 Header 既浪费空间
 *    又抢视觉重心。壳的职责是做 Web 做不到的事（原生网络、通知、文件），
 *    不是和 Web 抢地皮。
 *  - 因此「PING JS」从按钮下放到桥里：window.SoulLinkNative.pingJs()。
 *    Web 的 RAW 调试面板加个按钮即可触发，比原生 OptionMenu 更贴合架构
 *    （无 ActionBar 就没有菜单落点，硬塞 Toolbar 等于把顶栏请回来）。
 *  - 状态栏 / 导航栏刷成与 Web 背景同色（#0F172A = tailwind surface-900），
 *    图标保持浅色，视觉上无缝。
 *  - **没有做真正的 edge-to-edge（内容钻到状态栏底下）**：Web 顶栏没有吃
 *    env(safe-area-inset-top)，钻下去会被状态栏图标压住。要真沉浸式，
 *    需要 Web 侧给 header 加 safe-area 内边距后再改这里（见 README）。
 *
 * 保留的能力：返回键接管、文件选择器（角色卡导入）、原生 SSE 通道。
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
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

        setupSystemBars()

        // 先起本地内核服务器（同步 bind，极快），再进 Compose 挂 WebView
        server = LocalAssetServer(this).also { it.start() }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(WEB_BACKGROUND_ARGB)
            ) {
                AndroidView(
                    factory = { ctx -> createWebView(ctx) },
                    // 关键：撑满整个可用区域，不再被任何原生栏切一块
                    modifier = Modifier.fillMaxSize()
                )
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

    /**
     * 状态栏 / 导航栏与 Web 背景同色，图标保持浅色。
     * 注意：内容仍从状态栏下方开始（decorFitsSystemWindows 默认 true），
     * 不做真 edge-to-edge，避免 Web 顶栏被状态栏图标压住。
     */
    private fun setupSystemBars() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = WEB_BACKGROUND_ARGB
        window.navigationBarColor = WEB_BACKGROUND_ARGB

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(ctx: Context): WebView {
        return WebView(ctx).apply {
            this@MainActivity.webView = this

            // 避免加载瞬间闪白底
            setBackgroundColor(WEB_BACKGROUND_ARGB)

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // 内核走 http://127.0.0.1，不需要 file 访问，收紧权限
            settings.allowFileAccess = false
            settings.allowContentAccess = false

            // 桥：JS 调 window.SoulLinkNative.*，Kotlin 反向走 evaluateJavascript
            addJavascriptInterface(
                WebAppInterface(this) { newTitle ->
                    // 无原生顶栏后，标题只体现在任务切换卡片 / 最近任务里
                    this@MainActivity.setTitle(newTitle)
                },
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

        /** 与 tailwind `surface-900` (#0f172a) 对齐 */
        private val WEB_BACKGROUND_ARGB = 0xFF0F172A.toInt()
    }
}