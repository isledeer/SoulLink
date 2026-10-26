package com.isledeer.soullink.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isledeer.soullink.data.AppSettings
import com.isledeer.soullink.data.AssistantDao
import com.isledeer.soullink.data.AssistantEntity
import com.isledeer.soullink.data.ConfigResolver
import com.isledeer.soullink.data.ConversationDao
import com.isledeer.soullink.data.ConversationEntity
import com.isledeer.soullink.data.Feature
import com.isledeer.soullink.data.MessageParts
import com.isledeer.soullink.data.MessageTreeRepository
import com.isledeer.soullink.data.ResolvedConfig
import com.isledeer.soullink.data.SettingsRepository
import com.isledeer.soullink.data.UIMessageEntity
import com.isledeer.soullink.net.ChatRequest
import com.isledeer.soullink.net.ChatTurn
import com.isledeer.soullink.net.SseClient
import com.isledeer.soullink.net.SseEvent
import com.isledeer.soullink.tools.LocalTool
import com.isledeer.soullink.tools.LocalTools
import com.isledeer.soullink.util.Speaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.random.Random

/**
 * 多智能体聊天状态机。
 *
 * 配置链：全局默认 < 助手 < 对话覆盖（见 ConfigResolver）。
 * 消息：树形（节点 + 变体），重新生成 = 追加变体而不是删除。
 */
class ChatViewModel(
    private val sse: SseClient,
    private val settingsRepository: SettingsRepository,
    private val conversationDao: ConversationDao,
    private val assistantDao: AssistantDao,
    private val tree: MessageTreeRepository,
    private val tools: LocalTools,
    private val speaker: Speaker,
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _assistants = MutableStateFlow<List<AssistantEntity>>(emptyList())
    val assistants: StateFlow<List<AssistantEntity>> = _assistants.asStateFlow()

    private val _currentAssistant = MutableStateFlow<AssistantEntity?>(null)
    val currentAssistant: StateFlow<AssistantEntity?> = _currentAssistant.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _current = MutableStateFlow<Conversation?>(null)
    val current: StateFlow<Conversation?> = _current.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private var streamJob: Job? = null
    private var streamingId: String? = null

    init {
        viewModelScope.launch { settingsRepository.flow.collect { _settings.value = it } }
        viewModelScope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        if (assistantDao.loadAll().isEmpty()) {
            assistantDao.upsert(AssistantEntity.defaultAssistant())
        }
        _assistants.value = assistantDao.loadAll()

        val stored = conversationDao.loadAll()
        val list = if (stored.isEmpty()) {
            val fresh = newConversationEntity(_assistants.value.first())
            conversationDao.upsert(fresh)
            listOf(fresh)
        } else {
            stored
        }

        _conversations.value = list.map { it.toUi() }
        _current.value = list.first().toUi()
        _currentAssistant.value = assistantDao.findById(list.first().assistantId)
            ?: _assistants.value.firstOrNull()
        reloadMessages(list.first().id)
    }

    /* ------------------------------ 会话 ------------------------------ */

    fun newConversation() {
        stop()
        viewModelScope.launch {
            val assistant = _currentAssistant.value ?: _assistants.value.firstOrNull()
            val entity = newConversationEntity(assistant)
            conversationDao.upsert(entity)
            _conversations.update { listOf(entity.toUi()) + it }
            _current.value = entity.toUi()
            _messages.value = emptyList()
        }
    }

    fun selectConversation(id: String) {
        if (_current.value?.id == id) return
        stop()
        viewModelScope.launch {
            val entity = conversationDao.findById(id) ?: return@launch
            _current.value = entity.toUi()
            _currentAssistant.value = assistantDao.findById(entity.assistantId)
                ?: _assistants.value.firstOrNull()
            reloadMessages(id)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationDao.delete(id)
            tree.clear(id)
            val remaining = conversationDao.loadAll()
            if (remaining.isEmpty()) {
                val fresh = newConversationEntity(_assistants.value.firstOrNull())
                conversationDao.upsert(fresh)
                _conversations.value = listOf(fresh.toUi())
                _current.value = fresh.toUi()
                _messages.value = emptyList()
            } else {
                _conversations.value = remaining.map { it.toUi() }
                if (_current.value?.id == id) {
                    _current.value = remaining.first().toUi()
                    reloadMessages(remaining.first().id)
                }
            }
        }
    }

    /** 对话级覆盖：SystemPrompt / maxTokens / temperature */
    fun updateCurrentConversation(
        systemPrompt: String? = null,
        maxTokens: Int? = null,
        temperature: Double? = null,
    ) {
        val conv = _current.value ?: return
        val updated = conv.copy(
            systemPrompt = systemPrompt ?: conv.systemPrompt,
            maxTokens = maxTokens ?: conv.maxTokens,
            temperature = temperature ?: conv.temperature,
        )
        _current.value = updated
        viewModelScope.launch {
            conversationDao.upsert(updated.toEntity())
            _conversations.update { list -> list.map { if (it.id == updated.id) updated else it } }
        }
    }

    /* ------------------------------ 消息 ------------------------------ */

    fun send(text: String, imageBase64: String? = null) {
        val conv = _current.value ?: return
        if (_isGenerating.value) return
        if (text.isBlank() && imageBase64 == null) return

        viewModelScope.launch {
            val parentId = _messages.value.lastOrNull()?.nodeId
            val (userNodeId, _) = tree.appendTurn(
                conversationId = conv.id,
                parentId = parentId,
                role = Role.User.apiName,
                parts = MessageParts.encode(text.trim(), imageBase64),
                reasoning = null,
            )
            reloadMessages(conv.id)
            startGeneration(conv, parentNodeId = userNodeId, variantNodeId = null)
        }
    }

    /** 重新生成：给同一个节点追加变体，历史版本保留 */
    fun regenerate(messageId: String) {
        val conv = _current.value ?: return
        if (_isGenerating.value) return
        val target = _messages.value.firstOrNull { it.id == messageId } ?: return
        if (target.role != Role.Assistant) return

        viewModelScope.launch {
            startGeneration(conv, parentNodeId = null, variantNodeId = target.nodeId)
        }
    }

    fun deleteMessage(id: String) {
        val message = _messages.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            tree.deleteNode(message.nodeId)
            _messages.update { list -> list.filterNot { it.id == id } }
        }
    }

    fun editMessage(id: String, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return
        val message = _messages.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            tree.updateMessage(
                UIMessageEntity(
                    id = message.id,
                    nodeId = message.nodeId,
                    role = message.role.apiName,
                    parts = MessageParts.encode(trimmed, message.imageBase64),
                    reasoning = message.reasoning,
                    usageTokens = message.tokenCount,
                    generationSpeed = message.generationSpeed,
                    isFavorite = message.isFavorite,
                    createdAt = message.createdAt,
                )
            )
            _messages.update { list ->
                list.map { if (it.id == id) it.copy(text = trimmed) else it }
            }
        }
    }

    fun toggleFavorite(id: String) {
        val message = _messages.value.firstOrNull { it.id == id } ?: return
        _messages.update { list ->
            list.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it }
        }
        viewModelScope.launch {
            tree.updateMessage(
                UIMessageEntity(
                    id = message.id,
                    nodeId = message.nodeId,
                    role = message.role.apiName,
                    parts = MessageParts.encode(message.text, message.imageBase64),
                    reasoning = message.reasoning,
                    usageTokens = message.tokenCount,
                    generationSpeed = message.generationSpeed,
                    isFavorite = !message.isFavorite,
                    createdAt = message.createdAt,
                )
            )
        }
    }

    /** 从某条消息分叉：复制到该节点的整条路径到新会话 */
    fun branchFrom(messageId: String) {
        val source = _current.value ?: return
        val target = _messages.value.firstOrNull { it.id == messageId } ?: return
        val slice = _messages.value.takeWhile { true }.take(_messages.value.indexOf(target) + 1)

        viewModelScope.launch {
            val entity = ConversationEntity(
                id = newId(),
                title = "${source.title} · 分支",
                assistantId = source.assistantId,
                systemPrompt = source.systemPrompt,
                maxTokens = source.maxTokens,
                temperature = source.temperature,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            conversationDao.upsert(entity)
            var parent: String? = null
            slice.forEach { msg ->
                val (nodeId, _) = tree.appendTurn(
                    conversationId = entity.id,
                    parentId = parent,
                    role = msg.role.apiName,
                    parts = MessageParts.encode(msg.text, msg.imageBase64),
                    reasoning = msg.reasoning,
                )
                parent = nodeId
            }
            val all = conversationDao.loadAll()
            _conversations.value = all.map { it.toUi() }
            _current.value = entity.toUi()
            reloadMessages(entity.id)
        }
    }

    fun speak(text: String) = speaker.speak(text)

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        // 有已生成内容才标记「中断」，空占位不算
        streamingId?.let { id ->
            patchMessage(id) { it.copy(isStreaming = false, isInterrupted = it.text.isNotBlank()) }
        }
        streamingId = null
        _isGenerating.value = false
    }

    /** 继续生成：保留已生成的部分正文，接着往下补全 */
    fun continueGeneration(messageId: String) {
        val conv = _current.value ?: return
        if (_isGenerating.value) return
        val target = _messages.value.firstOrNull { it.id == messageId } ?: return
        if (target.role != Role.Assistant) return

        patchMessage(messageId) { it.copy(isInterrupted = false, isError = false) }
        viewModelScope.launch {
            startGeneration(conv, parentNodeId = target.nodeId, variantNodeId = null)
        }
    }

    /* ---------------------------- 生成流程 ---------------------------- */

    private suspend fun startGeneration(
        conversation: Conversation,
        parentNodeId: String?,
        variantNodeId: String?,
    ) {
        val assistant = _currentAssistant.value ?: _assistants.value.firstOrNull()
        val config = ConfigResolver.resolve(_settings.value, assistant, conversation.toEntity(), tools)

        if (!config.isConfigured) {
            _messages.update {
                it + ChatMessage(
                    id = "err-${newId()}",
                    nodeId = "err-node-${newId()}",
                    role = Role.Assistant,
                    text = "还没配置 Provider。进设置 → 提供商，填 Base URL 与 Key。",
                    isError = true,
                )
            }
            return
        }

        // 1) 建占位（新节点 或 给已有节点加变体）
        val nodeId: String
        val messageId: String
        if (variantNodeId == null) {
            val created = tree.appendTurn(
                conversationId = conversation.id,
                parentId = parentNodeId,
                role = Role.Assistant.apiName,
                parts = MessageParts.encode("", null),
                reasoning = null,
            )
            nodeId = created.first
            messageId = created.second
        } else {
            val node = tree.loadPath(conversation.id).firstOrNull { it.node.id == variantNodeId }?.node
            if (node == null) {
                return
            }
            val variant = tree.appendVariant(
                node = node,
                role = Role.Assistant.apiName,
                parts = MessageParts.encode("", null),
                reasoning = null,
            )
            nodeId = node.id
            messageId = variant.id
        }

        streamingId = messageId
        _isGenerating.value = true
        reloadMessages(conversation.id, markStreaming = messageId)

        // 2) 组请求
        val turns = buildTurns(config)
        val startedAt = SystemClock.elapsedRealtime()
        var usageTokens = 0
        var receivedChars = 0

        streamJob = viewModelScope.launch {
            var finalText = ""
            var finalReasoning = ""
            try {
                sse.stream(
                    ChatRequest(
                        baseUrl = config.provider?.baseUrl.orEmpty(),
                        apiKey = config.provider?.apiKey.orEmpty(),
                        model = config.model,
                        turns = turns,
                        systemPrompt = config.systemPrompt,
                        maxTokens = config.maxTokens,
                        temperature = config.temperature,
                        topP = config.topP,
                        toolSchemas = toolSchemas(config.tools),
                        extraHeaders = config.headers,
                        extraBody = config.bodyExtras,
                    )
                ).collect { event ->
                    when (event) {
                        is SseEvent.Content -> {
                            finalText += event.text
                            receivedChars += event.text.length
                            patchMessage(messageId) { it.copy(text = finalText) }
                        }

                        is SseEvent.Reasoning -> {
                            finalReasoning += event.text
                            patchMessage(messageId) { it.copy(reasoning = finalReasoning) }
                        }

                        is SseEvent.Usage -> if (event.completionTokens > 0) {
                            usageTokens = event.completionTokens
                        }

                        SseEvent.Done -> Unit
                    }
                }
            } catch (e: CancellationException) {
                // 用户停止：保留已生成
            } catch (e: Exception) {
                val reason = e.message ?: "未知错误"
                if (finalText.isBlank()) finalText = "请求失败：$reason"
                patchMessage(messageId) { it.copy(text = finalText, isError = true) }
            } finally {
                val elapsed = ((SystemClock.elapsedRealtime() - startedAt) / 1000.0).coerceAtLeast(0.001)
                val tokens = if (usageTokens > 0) usageTokens else (receivedChars / 2).coerceAtLeast(0)
                val speed = if (tokens > 0) tokens / elapsed else 0.0

                tree.updateMessage(
                    UIMessageEntity(
                        id = messageId,
                        nodeId = nodeId,
                        role = Role.Assistant.apiName,
                        parts = MessageParts.encode(finalText, null),
                        reasoning = finalReasoning.ifBlank { null },
                        usageTokens = tokens,
                        generationSpeed = speed,
                        createdAt = System.currentTimeMillis(),
                    )
                )

                streamingId = null
                _isGenerating.value = false
                reloadMessages(conversation.id)

                conversationDao.upsert(
                    conversation.toEntity().copy(updatedAt = System.currentTimeMillis())
                )
                _conversations.update { list ->
                    list.map {
                        if (it.id == conversation.id) it.copy(updatedAt = System.currentTimeMillis()) else it
                    }.sortedByDescending { it.updatedAt }
                }
            }
        }
    }

    private fun buildTurns(config: ResolvedConfig): List<ChatTurn> {
        val assistant = _currentAssistant.value
        val usable = _messages.value.filter {
            !it.isStreaming && !it.isError && (it.text.isNotBlank() || it.imageBase64 != null)
        }
        val limited = if (config.contextLimit > 0 && usable.size > config.contextLimit) {
            usable.takeLast(config.contextLimit)
        } else {
            usable
        }
        return limited.map { msg ->
            ChatTurn(
                role = msg.role.apiName,
                content = ConfigResolver.applyTemplate(
                    assistant?.messageTemplate,
                    msg.text,
                    msg.role.apiName,
                ),
                imageBase64 = msg.imageBase64,
            )
        }
    }

    private fun toolSchemas(enabled: List<LocalTool>): List<JSONObject> = enabled.map { tool ->
        JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", tool.name)
                    .put("description", tool.description)
                    .put("parameters", tool.parameters)
            )
    }

    private fun patchMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private suspend fun reloadMessages(conversationId: String, markStreaming: String? = null) {
        val turns = tree.loadPath(conversationId)
        _messages.value = turns.map { turn ->
            ChatMessage(
                id = turn.message.id,
                nodeId = turn.node.id,
                role = Role.fromApi(turn.message.role),
                text = MessageParts.text(turn.message.parts),
                reasoning = turn.message.reasoning,
                imageBase64 = MessageParts.image(turn.message.parts),
                isStreaming = turn.message.id == markStreaming,
                isError = turn.message.isError,
                isFavorite = turn.message.isFavorite,
                generationSpeed = turn.message.generationSpeed,
                tokenCount = turn.message.usageTokens,
                variantCount = turn.variantCount,
                variantIndex = turn.variantIndex,
                createdAt = turn.message.createdAt,
            )
        }
    }

    private fun newConversationEntity(assistant: AssistantEntity?): ConversationEntity {
        val now = System.currentTimeMillis()
        return ConversationEntity(
            id = newId(),
            title = "新对话",
            assistantId = assistant?.id ?: AssistantEntity.DEFAULT_ID,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun newId(): String = "${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}"

    private fun ConversationEntity.toUi() = Conversation(
        id = id,
        title = title,
        assistantId = assistantId,
        systemPrompt = systemPrompt,
        maxTokens = maxTokens,
        temperature = temperature,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun Conversation.toEntity() = ConversationEntity(
        id = id,
        title = title,
        assistantId = assistantId,
        systemPrompt = systemPrompt,
        maxTokens = maxTokens,
        temperature = temperature,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}