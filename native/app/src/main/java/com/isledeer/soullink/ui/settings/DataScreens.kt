package com.isledeer.soullink.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.isledeer.soullink.data.AppDatabase
import com.isledeer.soullink.data.AssistantEntity
import com.isledeer.soullink.data.ConversationEntity
import com.isledeer.soullink.data.GameEventEntity
import com.isledeer.soullink.data.GameSessionEntity
import com.isledeer.soullink.data.MessageNodeEntity
import com.isledeer.soullink.data.SettingsRepository
import com.isledeer.soullink.data.UIMessageEntity
import com.isledeer.soullink.net.LocalWebServer
import com.isledeer.soullink.ui.GlassTokens
import com.isledeer.soullink.ui.liquidGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/* ----------------------------- Web 服务器 ----------------------------- */

@Composable
fun WebServerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val server = remember { LocalWebServer(AppDatabase.get(context.applicationContext)) }
    var running by remember { mutableStateOf(server.running) }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(server.port.toString()) }
    var message by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        ip = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .asSequence()
                .flatMap { network -> network.inetAddresses.toList().asSequence() }
                .filterIsInstance<java.net.Inet4Address>()
                .firstOrNull { address -> !address.isLoopbackAddress }
                ?.hostAddress
                .orEmpty()
        }.getOrDefault("")
    }

    PageShell("Web 服务器", onBack) {
        Text(
            text = "开启后，同一 WiFi 下的电脑浏览器可以只读访问 SoulLink 的会话数据（不做写操作）。",
            color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp),
        )
        Spacer(Modifier.height(10.dp))
        SettingRow(
            title = "启用局域网访问",
            subtitle = if (running) "运行中" else "已停止",
            tail = {
                Switch(
                    checked = running,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            val ok = server.start()
                            running = ok
                            message = if (ok) "已启动" else "启动失败：端口被占用？"
                        } else {
                            server.stop()
                            running = false
                            message = "已停止"
                        }
                    },
                )
            },
        )
        if (running && ip.isNotBlank()) {
            val url = "http://$ip:$port"
            Spacer(Modifier.height(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                qrForShare(url)?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "访问二维码",
                        modifier = Modifier.size(180.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(url, color = GlassTokens.Primary, fontSize = 12.sp)
            }
        }
        message?.let {
            Text(it, color = GlassTokens.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 14.dp, top = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun qrForShare(content: String, size: Int = 512): android.graphics.Bitmap? = runCatching {
    val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
        content, com.google.zxing.BarcodeFormat.QR_CODE, size, size
    )
    android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
    }
}.getOrNull()

/* ----------------------------- 数据备份 ----------------------------- */

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val db = remember { AppDatabase.get(context.applicationContext) }
    val settingsRepo = remember { SettingsRepository(context.applicationContext) }

    val pickBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty() }
                        .getOrDefault("")
                }
                val restored = withContext(Dispatchers.IO) { restoreFromJson(db, settingsRepo, result) }
                message = if (restored) "导入完成" else "导入失败：不是有效的备份 JSON"
            }
        }
    }

    PageShell("数据备份", onBack) {
        Text(
            text = "备份包含：设置、提供商、助手、会话、消息树、游戏账本。\n导入会与现有数据合并（相同 id 覆盖）。",
            color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                scope.launch {
                    val json = withContext(Dispatchers.IO) { buildBackupJson(db, settingsRepo) }
                    val ok = withContext(Dispatchers.IO) {
                        val dir = File(context.cacheDir, "backups")
                        dir.mkdirs()
                        val file = File(dir, "soullink-backup-${System.currentTimeMillis()}.json")
                        runCatching {
                            file.writeText(json)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "导出 SoulLink 备份"))
                            true
                        }.getOrDefault(false)
                    }
                    if (!ok) message = "导出失败"
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(46.dp),
        ) { Text("导出备份并分享", fontSize = 14.sp) }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { pickBackup.launch(arrayOf("application/json", "text/*", "*/*")) },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary.copy(alpha = 0.2f), contentColor = GlassTokens.Primary),
            modifier = Modifier.fillMaxWidth().height(46.dp),
        ) { Text("从文件导入备份", fontSize = 14.sp) }

        message?.let {
            Text(it, color = GlassTokens.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 14.dp, top = 10.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

private suspend fun buildBackupJson(db: AppDatabase, settingsRepo: SettingsRepository): String = withContext(Dispatchers.IO) {
    val assistants = db.assistantDao().loadAll()
    val conversations = db.conversationDao().loadAll()
    val sessions = runCatching {
        // 游戏存档表没有 listAll，遍历会话拿到 sessionId 去查
        conversations.mapNotNull { it.sessionId }.distinct().mapNotNull { id -> db.gameSessionDao().findById(id) }
    }.getOrDefault(emptyList())

    val nodes = mutableListOf<MessageNodeEntity>()
    val messages = mutableListOf<UIMessageEntity>()
    conversations.forEach { conversation ->
        nodes += db.messageTreeDao().loadNodes(conversation.id)
        messages += db.messageTreeDao().loadAllMessages(conversation.id)
    }
    val events = sessions.flatMap { db.gameEventDao().forSession(it.id) }
    val worldbooks = db.worldBookDao().loadAll()
    val worldEntries = worldbooks.flatMap { db.worldBookDao().entriesOf(it.id) }
    val settingsJson = runBlocking { settingsRepo.flow.first().toJson() }

    JSONObject()
        .put("app", "soullink")
        .put("version", 1)
        .put("settingsJson", settingsJson)
        .put("assistants", JSONArray().also { array ->
            assistants.forEach { assistant ->
                array.put(
                    JSONObject()
                        .put("id", assistant.id).put("name", assistant.name).put("avatar", assistant.avatar)
                        .put("tags", assistant.tags).put("chatModelId", assistant.chatModelId)
                        .put("temperature", assistant.temperature).put("topP", assistant.topP)
                        .put("maxTokens", assistant.maxTokens).put("systemPrompt", assistant.systemPrompt)
                        .put("messageTemplate", assistant.messageTemplate)
                        .put("contextMessageLimit", assistant.contextMessageLimit)
                        .put("enableMemory", assistant.enableMemory).put("localTools", assistant.localTools)
                        .put("mcpServers", assistant.mcpServers).put("enabledSkills", assistant.enabledSkills)
                        .put("customHeaders", assistant.customHeaders).put("customBodies", assistant.customBodies)
                        .put("workspaceId", assistant.workspaceId).put("enableWebSearch", assistant.enableWebSearch)
                        .put("quickPrompts", assistant.quickPrompts)
                        .put("createdAt", assistant.createdAt).put("updatedAt", assistant.updatedAt)
                )
            }
        })
        .put("conversations", JSONArray().also { array ->
            conversations.forEach { conversation ->
                array.put(
                    JSONObject()
                        .put("id", conversation.id).put("title", conversation.title)
                        .put("assistantId", conversation.assistantId).put("sessionId", conversation.sessionId)
                        .put("systemPrompt", conversation.systemPrompt).put("maxTokens", conversation.maxTokens)
                        .put("temperature", conversation.temperature)
                        .put("createdAt", conversation.createdAt).put("updatedAt", conversation.updatedAt)
                )
            }
        })
        .put("sessions", JSONArray().also { array ->
            sessions.forEach { session ->
                array.put(
                    JSONObject()
                        .put("id", session.id).put("title", session.title)
                        .put("worldbookId", session.worldbookId).put("characterId", session.characterId)
                        .put("initialStateJson", session.initialStateJson)
                        .put("createdAt", session.createdAt).put("updatedAt", session.updatedAt)
                )
            }
        })
        .put("nodes", JSONArray().also { array ->
            nodes.forEach { node ->
                array.put(
                    JSONObject()
                        .put("id", node.id).put("conversationId", node.conversationId)
                        .put("parentId", node.parentId).put("selectIndex", node.selectIndex)
                        .put("createdAt", node.createdAt)
                )
            }
        })
        .put("messages", JSONArray().also { array ->
            messages.forEach { message ->
                array.put(
                    JSONObject()
                        .put("id", message.id).put("nodeId", message.nodeId).put("role", message.role)
                        .put("parts", message.parts).put("reasoning", message.reasoning)
                        .put("usageTokens", message.usageTokens).put("generationSpeed", message.generationSpeed)
                        .put("isError", message.isError).put("isFavorite", message.isFavorite)
                        .put("createdAt", message.createdAt)
                )
            }
        })
        .put("worldbooks", JSONArray().also { array ->
            worldbooks.forEach { book ->
                array.put(
                    JSONObject()
                        .put("id", book.id).put("title", book.title).put("template", book.template)
                        .put("docJson", book.docJson)
                        .put("createdAt", book.createdAt).put("updatedAt", book.updatedAt)
                )
            }
        })
        .put("worldEntries", JSONArray().also { array ->
            worldEntries.forEach { entry ->
                array.put(
                    JSONObject()
                        .put("id", entry.id).put("worldbookId", entry.worldbookId)
                        .put("keysJson", entry.keysJson).put("content", entry.content)
                        .put("alwaysOn", entry.alwaysOn).put("priority", entry.priority)
                )
            }
        })
        .put("events", JSONArray().also { array ->
            events.forEach { event ->
                array.put(
                    JSONObject()
                        .put("id", event.id).put("sessionId", event.sessionId)
                        .put("conversationId", event.conversationId).put("messageId", event.messageId)
                        .put("nodeId", event.nodeId).put("seq", event.seq).put("kind", event.kind)
                        .put("targetId", event.targetId).put("payload", event.payload)
                        .put("source", event.source).put("createdAt", event.createdAt)
                )
            }
        })
        .toString()
}

private suspend fun restoreFromJson(db: AppDatabase, settingsRepo: SettingsRepository, raw: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(raw)
            if (root.optString("app") != "soullink") return@withContext false

            root.optString("settingsJson").takeIf { it.isNotBlank() }?.let { json ->
                settingsRepo.save(com.isledeer.soullink.data.AppSettings.fromJson(json))
            }

            root.optJSONArray("assistants")?.let { array ->
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    db.assistantDao().upsert(
                        AssistantEntity(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            avatar = obj.optString("avatar").ifBlank { null },
                            tags = obj.optString("tags", "[]"),
                            chatModelId = obj.optString("chatModelId").ifBlank { null },
                            temperature = if (obj.has("temperature")) obj.optDouble("temperature").toFloat() else null,
                            topP = if (obj.has("topP")) obj.optDouble("topP").toFloat() else null,
                            maxTokens = if (obj.has("maxTokens")) obj.optInt("maxTokens") else null,
                            systemPrompt = obj.optString("systemPrompt"),
                            messageTemplate = obj.optString("messageTemplate").ifBlank { null },
                            contextMessageLimit = obj.optInt("contextMessageLimit"),
                            enableMemory = obj.optBoolean("enableMemory"),
                            localTools = obj.optString("localTools", "[]"),
                            mcpServers = obj.optString("mcpServers", "[]"),
                            enabledSkills = obj.optString("enabledSkills", "[]"),
                            customHeaders = obj.optString("customHeaders", "{}"),
                            customBodies = obj.optString("customBodies", "{}"),
                            workspaceId = obj.optString("workspaceId").ifBlank { null },
                            enableWebSearch = obj.optBoolean("enableWebSearch"),
                            quickPrompts = obj.optString("quickPrompts", "[]"),
                            createdAt = obj.optLong("createdAt"),
                            updatedAt = obj.optLong("updatedAt"),
                        )
                    )
                }
            }

            root.optJSONArray("conversations")?.let { array ->
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    db.conversationDao().upsert(
                        ConversationEntity(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            assistantId = obj.optString("assistantId"),
                            sessionId = obj.optString("sessionId").ifBlank { null },
                            systemPrompt = obj.optString("systemPrompt"),
                            maxTokens = obj.optInt("maxTokens"),
                            temperature = obj.optDouble("temperature"),
                            createdAt = obj.optLong("createdAt"),
                            updatedAt = obj.optLong("updatedAt"),
                        )
                    )
                }
            }

            root.optJSONArray("sessions")?.let { array ->
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    db.gameSessionDao().upsert(
                        GameSessionEntity(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            worldbookId = obj.optString("worldbookId").ifBlank { null },
                            characterId = obj.optString("characterId").ifBlank { null },
                            initialStateJson = obj.optString("initialStateJson", "{}"),
                            createdAt = obj.optLong("createdAt"),
                            updatedAt = obj.optLong("updatedAt"),
                        )
                    )
                }
            }

            root.optJSONArray("nodes")?.let { array ->
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    db.messageTreeDao().upsertNode(
                        MessageNodeEntity(
                            id = obj.optString("id"),
                            conversationId = obj.optString("conversationId"),
                            parentId = obj.optString("parentId").ifBlank { null },
                            selectIndex = obj.optInt("selectIndex"),
                            createdAt = obj.optLong("createdAt"),
                        )
                    )
                }
            }

            root.optJSONArray("messages")?.let { array ->
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    db.messageTreeDao().upsertMessage(
                        UIMessageEntity(
                            id = obj.optString("id"),
                            nodeId = obj.optString("nodeId"),
                            role = obj.optString("role"),
                            parts = obj.optString("parts"),
                            reasoning = obj.optString("reasoning").ifBlank { null },
                            usageTokens = obj.optInt("usageTokens"),
                            generationSpeed = obj.optDouble("generationSpeed"),
                            isError = obj.optBoolean("isError"),
                            isFavorite = obj.optBoolean("isFavorite"),
                            createdAt = obj.optLong("createdAt"),
                        )
                    )
                }
            }

            root.optJSONArray("worldbooks")?.let { array ->
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    db.worldBookDao().upsert(
                        com.isledeer.soullink.data.WorldBookEntity(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            template = obj.optString("template", "custom"),
                            docJson = obj.optString("docJson", "{}"),
                            createdAt = obj.optLong("createdAt"),
                            updatedAt = obj.optLong("updatedAt"),
                        )
                    )
                }
            }

            root.optJSONArray("worldEntries")?.let { array ->
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    db.worldBookDao().upsertEntry(
                        com.isledeer.soullink.data.WorldEntryEntity(
                            id = obj.optString("id"),
                            worldbookId = obj.optString("worldbookId"),
                            keysJson = obj.optString("keysJson", "[]"),
                            content = obj.optString("content"),
                            alwaysOn = obj.optBoolean("alwaysOn"),
                            priority = obj.optInt("priority"),
                        )
                    )
                }
            }

            root.optJSONArray("events")?.let { array ->
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    db.gameEventDao().insertAll(
                        listOf(
                            GameEventEntity(
                                id = obj.optString("id"),
                                sessionId = obj.optString("sessionId"),
                                conversationId = obj.optString("conversationId"),
                                messageId = obj.optString("messageId"),
                                nodeId = obj.optString("nodeId"),
                                seq = obj.optLong("seq"),
                                kind = obj.optString("kind"),
                                targetId = obj.optString("targetId").ifBlank { null },
                                payload = obj.optString("payload", "{}"),
                                source = obj.optString("source", "TOOL"),
                                createdAt = obj.optLong("createdAt"),
                            )
                        )
                    )
                }
            }

            true
        }.getOrDefault(false)
    }

/* ----------------------------- 存储管理 ----------------------------- */

@Composable
fun StorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.get(context.applicationContext) }
    var stats by remember { mutableStateOf("计算中…") }
    var confirmVisible by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        stats = withContext(Dispatchers.IO) {
            val conversations = db.conversationDao().loadAll()
            val messages = conversations.flatMap { db.messageTreeDao().loadAllMessages(it.id) }
            val sessions = conversations.mapNotNull { it.sessionId }.distinct()
                .mapNotNull { db.gameSessionDao().findById(it) }
            val events = sessions.flatMap { db.gameEventDao().forSession(it.id) }
            val dbSize = context.getDatabasePath("soullink.db").length()
            val settingsSize = File(context.filesDir, "datastore/soullink_settings.preferences_pb").length()
            "会话 ${conversations.size} · 消息 ${messages.size}\n" +
                "游戏存档 ${sessions.size} · 账本事件 ${events.size}\n" +
                "数据库 ${formatBytes(dbSize)} · 设置 ${formatBytes(settingsSize)}"
        }
    }

    PageShell("存储管理", onBack) {
        Text(
            text = stats,
            color = GlassTokens.TextPrimary,
            fontSize = 12.sp,
            lineHeight = 19.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .liquidGlass(cornerRadius = 18.dp)
                .padding(14.dp),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { confirmVisible = true },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Accent, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(46.dp),
        ) { Text("清空全部数据", fontSize = 14.sp) }
        message?.let {
            Text(it, color = GlassTokens.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 14.dp, top = 10.dp))
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmVisible) {
        Dialog(onDismissRequest = { confirmVisible = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassTokens.BackgroundBottom.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Text("清空全部数据？", color = GlassTokens.TextPrimary, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "删除全部会话、消息、助手（保留默认助手）、游戏存档与账本。设置与 Key 不受影响。此操作不可撤销。",
                    color = GlassTokens.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { confirmVisible = false }) { Text("取消", color = GlassTokens.TextSecondary) }
                    TextButton(
                        onClick = {
                            confirmVisible = false
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val sessionIds = db.conversationDao().loadAll()
                                        .mapNotNull { it.sessionId }
                                        .distinct()
                                    db.conversationDao().loadAll().forEach { conversation ->
                                        db.messageTreeDao().clearMessages(conversation.id)
                                        db.messageTreeDao().clearNodes(conversation.id)
                                        db.gameEventDao().deleteForConversation(conversation.id)
                                        db.conversationDao().delete(conversation.id)
                                    }
                                    sessionIds.forEach { sessionId -> db.gameSessionDao().delete(sessionId) }
                                    db.worldBookDao().loadAll().forEach { book ->
                                        db.worldBookDao().clearEntries(book.id)
                                        db.worldBookDao().delete(book.id)
                                    }
                                    db.assistantDao().loadAll().forEach { assistant ->
                                        if (assistant.id != AssistantEntity.DEFAULT_ID) {
                                            db.assistantDao().delete(assistant.id)
                                        }
                                    }
                                }
                                message = "已清空"
                            }
                        },
                    ) { Text("清空", color = GlassTokens.Accent) }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}