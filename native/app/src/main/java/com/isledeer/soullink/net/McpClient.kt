package com.isledeer.soullink.net

import com.isledeer.soullink.data.McpServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 一个 MCP 工具的描述 */
data class McpToolInfo(
    val serverId: String,
    val serverName: String,
    val name: String,
    val description: String,
    val inputSchema: JSONObject,
)

/**
 * 极简 MCP 客户端（Streamable HTTP）。
 * 只支持 HTTP 端点：initialize → tools/list → tools/call。
 * 不支持 stdio（Android 上不好起子进程），够覆盖大多数远程 MCP 服务。
 */
class McpClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val PROTOCOL_VERSION = "2024-11-05"
    }

    private val sessions = mutableMapOf<String, String>()

    suspend fun listTools(server: McpServerConfig): Result<List<McpToolInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                rpc(
                    server = server,
                    id = 1,
                    method = "initialize",
                    params = JSONObject()
                        .put("protocolVersion", PROTOCOL_VERSION)
                        .put("capabilities", JSONObject())
                        .put(
                            "clientInfo",
                            JSONObject().put("name", "SoulLink").put("version", "1.0")
                        ),
                )

                val response = rpc(server, 2, "tools/list", JSONObject())
                val array = response.optJSONObject("result")?.optJSONArray("tools") ?: JSONArray()
                val tools = mutableListOf<McpToolInfo>()
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val name = item.optString("name")
                    if (name.isBlank()) continue
                    tools.add(
                        McpToolInfo(
                            serverId = server.id,
                            serverName = server.name,
                            name = name,
                            description = item.optString("description"),
                            inputSchema = item.optJSONObject("inputSchema")
                                ?: JSONObject().put("type", "object").put("properties", JSONObject()),
                        )
                    )
                }
                tools
            }
        }

    suspend fun callTool(
        server: McpServerConfig,
        toolName: String,
        args: JSONObject,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = rpc(
                server = server,
                id = 3,
                method = "tools/call",
                params = JSONObject().put("name", toolName).put("arguments", args),
            )
            val result = response.optJSONObject("result")
                ?: throw IllegalStateException("MCP 无结果")
            if (result.optBoolean("isError")) {
                throw IllegalStateException(result.optString("content", "MCP 工具报错"))
            }
            val content = result.optJSONArray("content")
            if (content == null) {
                result.toString()
            } else {
                val parts = mutableListOf<String>()
                for (i in 0 until content.length()) {
                    val item = content.optJSONObject(i) ?: continue
                    if (item.optString("type") == "text") {
                        parts.add(item.optString("text"))
                    } else {
                        parts.add(item.toString())
                    }
                }
                parts.joinToString("\n")
            }
        }
    }

    private fun rpc(
        server: McpServerConfig,
        id: Int,
        method: String,
        params: JSONObject,
    ): JSONObject {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)

        val builder = Request.Builder()
            .url(server.url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))

        if (server.apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer ${server.apiKey}")
        }
        sessions[server.id]?.let { sessionId -> builder.addHeader("Mcp-Session-Id", sessionId) }

        client.newCall(builder.build()).execute().use { response ->
            response.header("Mcp-Session-Id")
                ?.takeIf { it.isNotBlank() }
                ?.let { sessionId -> sessions[server.id] = sessionId }

            if (!response.isSuccessful) {
                throw IllegalStateException("MCP HTTP ${response.code}")
            }

            val contentType = response.header("Content-Type").orEmpty()
            val text = response.body?.string().orEmpty()

            if (contentType.contains("text/event-stream")) {
                val payload = text.lineSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("data:") }
                    .map { it.removePrefix("data:").trim() }
                    .firstOrNull { it.startsWith("{") && it.contains("\"jsonrpc\"") }
                    ?: throw IllegalStateException("MCP 事件流里没有 JSON-RPC 响应")
                return JSONObject(payload)
            }

            return JSONObject(text)
        }
    }
}