package com.isledeer.soullink

import android.content.Context
import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 极简本地 HTTP 服务器：把 assets/ 里的 React 内核以
 * http://127.0.0.1:<port>/ 提供给 WebView。
 *
 * 为什么不用 file:///android_asset：
 *  1. file:// 页面 origin 是 "null"，向第三方 API 发请求时 CORS 预检几乎必挂
 *     （不少网关不放行 null origin），而现在内核已全部走 https 直连；
 *  2. Android WebView 在 file:// 下 IndexedDB / localStorage 行为不稳定，
 *     而 SoulLink 的存档、角色卡、设置全押在 IndexedDB 上，不能赌。
 * http://127.0.0.1 是真实 origin，两个问题一并消失。
 *
 * 端口必须**固定**：换了端口就等于换了 origin，用户存档会「凭空消失」。
 * 默认 38517，被占用时依次 +1 兜底（此时该次运行读不到旧数据，属极端情况）。
 */
@Deprecated("WebView 时代遗留，世界书已改用原生 Compose 页面，此类仅留档。")
class LocalAssetServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private var port = -1

    fun start(): Boolean {
        for (candidate in PORTS) {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("127.0.0.1", candidate))
                serverSocket = socket
                port = candidate
                break
            } catch (e: IOException) {
                Log.w(TAG, "端口 $candidate 绑定失败: ${e.message}")
            }
        }

        if (serverSocket == null) {
            Log.e(TAG, "内核服务器启动失败，将退回 file:// 模式")
            return false
        }

        running.set(true)
        Thread({ acceptLoop() }, "soullink-httpd").start()
        Log.i(TAG, "内核已就绪: http://127.0.0.1:$port/")
        return true
    }

    fun entryUrl(): String = "http://127.0.0.1:$port/index.html"

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    private fun acceptLoop() {
        while (running.get()) {
            val socket = try {
                serverSocket?.accept()
            } catch (e: Exception) {
                null
            } ?: break
            Thread({ handle(socket) }, "soullink-http-conn").start()
        }
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            try {
                val reader = s.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val rawPath = parts[1].substringBefore("?")
                // 只处理静态 GET，读完请求头即可
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }

                val path = normalize(rawPath)
                val bytes = try {
                    context.assets.open(path).use { it.readBytes() }
                } catch (e: IOException) {
                    null
                }

                val out = s.getOutputStream()
                if (bytes == null) {
                    out.write(
                        ("HTTP/1.1 404 Not Found\r\n" +
                            "Content-Type: text/plain; charset=utf-8\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n\r\n").toByteArray()
                    )
                } else {
                    val header =
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: ${contentType(path)}\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Connection: close\r\n\r\n"
                    out.write(header.toByteArray())
                    out.write(bytes)
                }
                out.flush()
            } catch (e: Exception) {
                Log.d(TAG, "请求处理失败: ${e.message}")
            }
        }
    }

    /** 防路径穿越 */
    private fun normalize(path: String): String {
        val cleaned = path.trimStart('/')
        return when {
            cleaned.isEmpty() -> "index.html"
            cleaned.contains("..") -> "index.html"
            else -> cleaned
        }
    }

    private fun contentType(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        val base = when (ext) {
            "html" -> "text/html"
            "js", "mjs" -> "text/javascript"
            "css" -> "text/css"
            "json", "map" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
        return if (base.startsWith("text/") || base == "application/json") {
            "$base; charset=utf-8"
        } else {
            base
        }
    }

    companion object {
        private const val TAG = "SoulLinkServer"
        private val PORTS = intArrayOf(38517, 38518, 38519)
    }
}