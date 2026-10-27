package com.isledeer.soullink.net

import com.isledeer.soullink.data.AppDatabase
import com.isledeer.soullink.data.MessageParts
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * 局域网 Web 服务（设置 → Web 服务器）。
 *
 * v1 只读：首页列出会话，/api/conversations 与 /api/messages 返回 JSON。
 * 端口默认 38518（手机与电脑同一局域网时，浏览器访问 http://<手机IP>:38518）。
 */
class LocalWebServer(private val db: AppDatabase) {

    @Volatile
    var running = false
        private set

    var port = 38518

    private var serverSocket: java.net.ServerSocket? = null
    private var thread: Thread? = null

    fun start(): Boolean {
        if (running) return true
        return try {
            val socket = java.net.ServerSocket(port)
            serverSocket = socket
            running = true
            thread = Thread {
                while (running) {
                    try {
                        val client = socket.accept()
                        Thread { handle(client) }.start()
                    } catch (e: Exception) {
                        if (!running) break
                    }
                }
            }
            thread?.start()
            true
        } catch (e: Exception) {
            running = false
            false
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handle(client: java.net.Socket) {
        try {
            client.getInputStream().bufferedReader().use { reader ->
                val line = reader.readLine() ?: return
                val parts = line.split(" ")
                val path = parts.getOrNull(1) ?: "/"
                val response = route(path)
                val bytes = response.toByteArray(Charsets.UTF_8)
                client.getOutputStream().apply {
                    write(
                        ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: ${if (path.startsWith("/api/")) "application/json; charset=utf-8" else "text/html; charset=utf-8"}\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n").toByteArray(Charsets.UTF_8)
                    )
                    write(bytes)
                    flush()
                }
            }
        } catch (e: Exception) {
            // 单连接失败不影响服务
        } finally {
            runCatching { client.close() }
        }
    }

    private fun route(path: String): String {
        return when {
            path == "/" || path == "/index.html" -> homePage()
            path == "/api/conversations" -> conversationsJson()
            path.startsWith("/api/messages") -> messagesJson(path)
            else -> "{\"error\":\"not found\"}"
        }
    }

    private fun homePage(): String {
        val conversations = runBlocking { db.conversationDao().loadAll() }
        val rows = conversations.joinToString("\n") { conversation ->
            """<li><a href="/api/messages?conversationId=${conversation.id}">${escapeHtml(conversation.title)}</a></li>"""
        }
        return """<!doctype html><html><head><meta charset="utf-8">
<title>SoulLink</title>
<style>body{font-family:sans-serif;background:#070b14;color:#e2e8f0;padding:24px}
a{color:#0ea5e9}h1{font-size:18px}li{margin:6px 0}</style>
</head><body><h1>SoulLink conversations (read-only)</h1><ul>${if (rows.isBlank()) "<li>No conversations yet</li>" else rows}</ul>
<p style="color:#64748b;font-size:12px">SoulLink LAN page: open http://&lt;phone-ip&gt;:38518 in a browser on the same Wi-Fi.</p>
</body></html>"""
    }

    private fun conversationsJson(): String {
        val conversations = runBlocking { db.conversationDao().loadAll() }
        val array = JSONArray()
        conversations.forEach { conversation ->
            array.put(
                JSONObject()
                    .put("id", conversation.id)
                    .put("title", conversation.title)
                    .put("assistantId", conversation.assistantId)
            )
        }
        return array.toString()
    }

    private fun messagesJson(path: String): String {
        val query = path.substringAfter("?", "")
        val conversationId = query.split("&").firstOrNull { it.startsWith("conversationId=") }
            ?.removePrefix("conversationId=")
        if (conversationId.isNullOrBlank()) return "{\"error\":\"missing conversationId\"}"

        val messages = runBlocking { db.messageTreeDao().loadAllMessages(conversationId) }
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("nodeId", message.nodeId)
                    .put("role", message.role)
                    .put("text", MessageParts.text(message.parts))
                    .put("createdAt", message.createdAt)
            )
        }
        return array.toString()
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}