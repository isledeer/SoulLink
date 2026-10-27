package com.isledeer.soullink.ui.worldbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isledeer.soullink.data.AssistantDao
import com.isledeer.soullink.data.AssistantEntity
import com.isledeer.soullink.data.ConversationDao
import com.isledeer.soullink.data.ConversationEntity
import com.isledeer.soullink.data.Feature
import com.isledeer.soullink.data.GameSessionDao
import com.isledeer.soullink.data.GameSessionEntity
import com.isledeer.soullink.data.SettingsRepository
import com.isledeer.soullink.data.WorldBookDao
import com.isledeer.soullink.data.WorldBookEntity
import com.isledeer.soullink.data.WorldDoc
import com.isledeer.soullink.data.WorldEntryEntity
import com.isledeer.soullink.data.WorldbookTemplates
import com.isledeer.soullink.net.ChatRequest
import com.isledeer.soullink.net.ChatTurn
import com.isledeer.soullink.net.SseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * 世界书管理 + 游戏会话创建。
 *
 * 「框架 + AI 填充」：模板只给骨架，内容由用户自己的 AI（CHAT 绑定）生成，
 * 生成后落到本地 worldbooks / world_entries，用户可继续编辑。
 */
class WorldbookViewModel(
    private val dao: WorldBookDao,
    private val sessionDao: GameSessionDao,
    private val conversationDao: ConversationDao,
    private val assistantDao: AssistantDao,
    private val settingsRepository: SettingsRepository,
    private val sse: SseClient,
) : ViewModel() {

    private val _books = MutableStateFlow<List<WorldBookEntity>>(emptyList())
    val books: StateFlow<List<WorldBookEntity>> = _books.asStateFlow()

    private val _detail = MutableStateFlow<WorldBookEntity?>(null)
    val detail: StateFlow<WorldBookEntity?> = _detail.asStateFlow()

    private val _entries = MutableStateFlow<List<WorldEntryEntity>>(emptyList())
    val entries: StateFlow<List<WorldEntryEntity>> = _entries.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun refresh() {
        viewModelScope.launch { _books.value = dao.loadAll() }
    }

    fun load(id: String) {
        viewModelScope.launch {
            _detail.value = dao.findById(id)
            _entries.value = dao.entriesOf(id)
        }
    }

    fun create(template: String, title: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val realTitle = title.ifBlank { "未命名世界" }
        val book = WorldBookEntity(
            id = id,
            title = realTitle,
            template = template,
            docJson = WorldbookTemplates.blank(template, realTitle),
            createdAt = now,
            updatedAt = now,
        )
        viewModelScope.launch {
            dao.upsert(book)
            refresh()
        }
        return id
    }

    fun save(book: WorldBookEntity, entries: List<WorldEntryEntity>) {
        viewModelScope.launch {
            dao.upsert(book.copy(updatedAt = System.currentTimeMillis()))
            dao.clearEntries(book.id)
            entries.forEach { entry -> dao.upsertEntry(entry.copy(worldbookId = book.id)) }
            refresh()
            _message.value = "已保存"
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            dao.delete(id)
            dao.clearEntries(id)
            refresh()
        }
    }

    fun importBook(raw: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val json = extractJson(raw)
            if (json == null) {
                _message.value = "导入失败：不是合法的世界书 JSON"
                onDone("导入失败：不是合法的世界书 JSON")
                return@launch
            }
            val doc = WorldDoc.parse(json)
            val now = System.currentTimeMillis()
            val book = WorldBookEntity(
                id = UUID.randomUUID().toString(),
                title = doc.title.ifBlank { "导入的世界" },
                template = "custom",
                docJson = json,
                createdAt = now,
                updatedAt = now,
            )
            dao.upsert(book)
            importEntries(book.id, json)
            refresh()
            _message.value = "已导入世界书"
            onDone(null)
        }
    }

    /** AI 填充：一次 complete() 生成 doc JSON + 条目 */
    fun aiFill(bookId: String, premise: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val book = dao.findById(bookId)
            if (book == null) {
                _busy.value = false
                _message.value = "世界书不存在"
                onDone("世界书不存在")
                return@launch
            }
            val settings = settingsRepository.flow.first()
            val provider = settings.providerFor(Feature.CHAT)
            if (provider == null || provider.baseUrl.isBlank()) {
                _busy.value = false
                _message.value = "还没有配置对话 Provider（设置 → 提供商）"
                onDone("还没有配置对话 Provider（设置 → 提供商）")
                return@launch
            }
            val result = runCatching {
                sse.complete(
                    ChatRequest(
                        baseUrl = provider.baseUrl,
                        apiKey = provider.apiKey,
                        model = settings.modelFor(Feature.CHAT),
                        turns = listOf(ChatTurn(role = "user", content = premise.ifBlank { book.title })),
                        systemPrompt = WorldbookTemplates.fillPrompt(book.template, premise.ifBlank { book.title }),
                        maxTokens = 2600,
                        temperature = 0.9,
                    )
                )
            }.getOrNull()
            val json = result?.let { extractJson(it) }
            if (json == null) {
                _busy.value = false
                _message.value = "AI 填充失败：返回不是合法 JSON（可换个模型重试）"
                onDone("AI 填充失败：返回不是合法 JSON（可换个模型重试）")
                return@launch
            }
            val doc = WorldDoc.parse(json)
            dao.upsert(
                book.copy(
                    title = doc.title.ifBlank { book.title },
                    docJson = json,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            dao.clearEntries(bookId)
            importEntries(bookId, json)
            _busy.value = false
            load(bookId)
            refresh()
            _message.value = "AI 填充完成，可继续编辑"
            onDone(null)
        }
    }

    private suspend fun importEntries(bookId: String, json: String) {
        runCatching {
            val root = JSONObject(json)
            root.optJSONArray("entries")?.let { array ->
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val content = obj.optString("content")
                    if (content.isBlank()) continue
                    dao.upsertEntry(
                        WorldEntryEntity(
                            id = UUID.randomUUID().toString(),
                            worldbookId = bookId,
                            keysJson = obj.optJSONArray("keys")?.toString() ?: "[]",
                            content = content,
                            alwaysOn = obj.optBoolean("alwaysOn"),
                            priority = obj.optInt("priority"),
                        )
                    )
                }
            }
        }
    }

    private fun extractJson(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val candidate = raw.substring(start, end + 1)
        return if (runCatching { JSONObject(candidate) }.isSuccess) candidate else null
    }

    /** 开始游戏：创建存档 + 主线会话 + KP 助手，返回会话 id */
    suspend fun startSession(bookId: String): String? {
        val book = dao.findById(bookId) ?: return null
        val doc = WorldDoc.parse(book.docJson)
        val now = System.currentTimeMillis()

        val assistantId = UUID.randomUUID().toString()
        assistantDao.upsert(
            AssistantEntity(
                id = assistantId,
                name = "世界书·${doc.title.ifBlank { book.title }}",
                systemPrompt = "你是这个世界的叙事者（KP/DM）。用第二人称「你」叙述，遵守世界规则，不替用户做决定。每次回复给出 4 个可行动选项。",
                temperature = 0.9f,
                maxTokens = 2048,
                localTools = "[\"game\",\"time\"]",
            )
        )

        val sessionId = UUID.randomUUID().toString()
        sessionDao.upsert(
            GameSessionEntity(
                id = sessionId,
                title = doc.title.ifBlank { book.title },
                worldbookId = bookId,
                initialStateJson = WorldbookTemplates.buildInitialState(doc),
                createdAt = now,
                updatedAt = now,
            )
        )

        val conversationId = UUID.randomUUID().toString()
        conversationDao.upsert(
            ConversationEntity(
                id = conversationId,
                title = doc.title.ifBlank { book.title },
                assistantId = assistantId,
                sessionId = sessionId,
                createdAt = now,
                updatedAt = now,
            )
        )
        return conversationId
    }

    /** 进入某个 NPC 的攻略线（同一本账，独立会话；已存在则复用），返回会话 id */
    suspend fun startNpcThread(sessionId: String, worldbookId: String, npcId: String): String? {
        val book = dao.findById(worldbookId) ?: return null
        val doc = WorldDoc.parse(book.docJson)
        val npc = doc.npcs.firstOrNull { it.id == npcId || it.name == npcId } ?: return null

        val existing = conversationDao.loadBySession(sessionId).firstOrNull { it.title == npc.name }
        if (existing != null) return existing.id

        val now = System.currentTimeMillis()
        val assistantId = UUID.randomUUID().toString()
        assistantDao.upsert(
            AssistantEntity(
                id = assistantId,
                name = "世界书·${doc.title}·${npc.name}",
                systemPrompt = "你是${npc.name}（${npc.role}）。${npc.brief}。你身处世界「${doc.title}」，用第一人称回应，不要跳出角色，不替用户做决定。",
                temperature = 0.9f,
                maxTokens = 2048,
                localTools = "[\"game\",\"time\"]",
            )
        )
        val conversationId = UUID.randomUUID().toString()
        conversationDao.upsert(
            ConversationEntity(
                id = conversationId,
                title = npc.name,
                assistantId = assistantId,
                sessionId = sessionId,
                createdAt = now,
                updatedAt = now,
            )
        )
        return conversationId
    }
}