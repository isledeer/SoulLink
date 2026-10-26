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
import com.isledeer.soullink.data.ProviderConfig
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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

    private val _tokenUsage = MutableStateFlow(0)
    val tokenUsage: StateFlow<Int> = _tokenUsage.asStateFlow()

    private val _thinkingLevel = MutableStateFlow("OFF")
    val thinkingLevel: StateFlow<String> = _thinkingLevel.asStateFlow()

    /** 顶栏副标题：助手覆盖 > 全局绑定；空 = 未选择模型 */
    private val _currentModelName = MutableStateFlow<String?>(null)
    val currentModelName: StateFlow<String?> = _currentModelName.asStateFlow()

    private fun refreshModelName() {
        val assistant = _currentAssistant.value
        val global = _settings.value.modelFor(Feature.CHAT)
        _currentModelName.value = assistant?.chatModelId?.takeIf { it.isNotBlank() } ?: global.takeIf { it.isNotBlank() }
    }

    fun setThinkingLevel(level: String) {
        if (level in listOf("OFF", "LOW", "MEDIUM", "HIGH", "XHIGH")) {
            _thinkingLevel.value = level
        }
    }

    private val _autoCompress = MutableStateFlow(false)
    val autoCompress: StateFlow<Boolean> = _autoCompress.asStateFlow()

    /** 联网搜索：总开关 + 四个引擎各自开关 */
    private val _searchMaster = MutableStateFlow(false)
    val searchMaster: StateFlow<Boolean> = _searchMaster.asStateFlow()

    private val _searchEngines = MutableStateFlow(
        mapOf("bing" to false, "bocha" to false, "tavily" to false, "exa" to false)
    )
    val searchEngines: StateFlow<Map<String, Boolean>> = _searchEngines.asStateFlow()

    /** 地球图标高亮 = 总开关 && 至少一个引擎开 */
    val isSearchEnabled: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        _searchMaster, _searchEngines
    ) { master, engines -> master && engines.values.any { it } }.stateIn(
        viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false
    )

    fun setSearchMaster(enabled: Boolean) { _searchMaster.value = enabled }
    fun setSearchEngine(id: String, enabled: Boolean) {
        _searchEngines.value = _searchEngines.value + (id to enabled)
    }
    fun searchKeyFor(id: String): String = _settings.value.searchKeys[id].orEmpty()

    private var streamJob: Job? = null
    private var streamingId: String? = null

    /** 流式中途的新消息队列：当前流结束后自动发出 */
    private val pendingSends = ArrayDeque<Pair<String, String?>>()

    init {
        viewModelScope.launch {
            settingsRepository.flow.collect { settings ->
                _settings.value = settings
                refreshModelName()
                val provider = settings.providerFor(Feature.CHAT)
                tools.imageGenerator = if (provider != null && provider.baseUrl.isNotBlank()) {
                    { prompt -> runBlocking { sse.generateImage(provider.baseUrl, provider.apiKey, prompt) } }
                } else {
                    null
                }
            }
        }
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
        refreshModelName()
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
            refreshModelName()
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
        if (_isGenerating.value) {
            // 队列控制：不打断当前流，排到队尾（上限 10 条防爆）
            if (pendingSends.size < 10) pendingSends.addLast(text.trim() to imageBase64)
            return
        }
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

    fun clearContext() {
        val conversation = _current.value ?: return
        stop()
        viewModelScope.launch {
            tree.clear(conversation.id)
            _messages.value = emptyList()
            _tokenUsage.value = 0
        }
    }

    fun toggleAutoCompress() {
        _autoCompress.value = !_autoCompress.value
    }

    /** 工具面板开关：增删当前助手的 localTools 并落库 */
    fun toggleLocalTool(id: String) {
        val assistant = _currentAssistant.value ?: return
        val list = assistant.toolList().toMutableList()
        if (list.contains(id)) list.remove(id) else list.add(id)
        val array = org.json.JSONArray()
        list.forEach { array.put(it) }
        val updated = assistant.copy(localTools = array.toString(), updatedAt = System.currentTimeMillis())
        _currentAssistant.value = updated
        viewModelScope.launch { assistantDao.upsert(updated) }
    }

    /** 顶栏模型选择：写入当前助手的 chatModelId */
    fun setAssistantModel(model: String) {
        val assistant = _currentAssistant.value ?: return
        val updated = assistant.copy(chatModelId = model, updatedAt = System.currentTimeMillis())
        _currentAssistant.value = updated
        refreshModelName()
        viewModelScope.launch { assistantDao.upsert(updated) }
    }

    suspend fun fetchModelsForChat(provider: ProviderConfig): Result<List<String>> =
        runCatching { sse.fetchModels(provider.baseUrl, provider.apiKey) }

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
            var finalImage: String? = null
            try {
                // 工具环路：模型发 tool_calls -> 本地执行 -> 回填 -> 自动续流（上限 MAX_TOOL_ROUNDS 轮）
                var turnsInFlight = turns
                var round = 0
                while (round < MAX_TOOL_ROUNDS) {
                    val toolCalls = mutableListOf<SseEvent.ToolCall>()

                    sse.stream(
                        ChatRequest(
                            baseUrl = config.provider?.baseUrl.orEmpty(),
                            apiKey = config.provider?.apiKey.orEmpty(),
                            model = config.model,
                            turns = turnsInFlight,
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

                            is SseEvent.ToolCall -> toolCalls.add(event)

                            SseEvent.Done -> Unit
                        }
                    }

                    if (toolCalls.isEmpty()) break

                    // 执行并回填 role:"tool" 消息
                    for (call in toolCalls) {
                        val result = executeToolCall(messageId, call)
                        if (result.imageBase64 != null) finalImage = result.imageBase64
                        turnsInFlight = turnsInFlight + ChatTurn(
                            role = "tool",
                            content = result.content,
                            toolCallId = result.toolCallId,
                        )
                    }
                    round++
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
                        parts = MessageParts.encode(finalText, finalImage),
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

                // 队列里有货：自动发下一条
                pendingSends.removeFirstOrNull()?.let { (queuedText, queuedImage) ->
                    send(queuedText, queuedImage)
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
        _tokenUsage.value = _messages.value.sumOf { it.tokenCount }
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

    /**
     * 执行一次工具调用，返回回填给 API 的 tool turn。
     * 生图类工具：base64 写进消息图片区，tool 回填内容用小文本。
     */
    private suspend fun executeToolCall(
        messageId: String,
        call: SseEvent.ToolCall,
    ): ToolTurnResult {
        val tool = tools.byName(call.name)
        val result = if (tool == null) {
            "未知工具：${call.name}"
        } else {
            try {
                val args = runCatching { JSONObject(call.arguments) }.getOrDefault(JSONObject())
                tool.executor(args)
            } catch (e: Exception) {
                "工具执行失败：${e.message ?: "未知错误"}"
            }
        }

        if (tool != null && tool.outputImage && result.isNotBlank() &&
            !result.startsWith("生图") && !result.startsWith("缺少") && !result.startsWith("未知")
        ) {
            patchMessage(messageId) { it.copy(imageBase64 = result) }
            return ToolTurnResult(call.id, "图片已生成，显示在回复中", null)
        }
        return ToolTurnResult(call.id, result, null)
    }

    private fun newId(): String = "${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}"

    private data class ToolTurnResult(
        val toolCallId: String,
        val content: String,
        val imageBase64: String?,
    )

    companion object {
        /** 单轮用户消息最多允许的工具往返次数（首轮 + 3 次工具续流） */
        private const val MAX_TOOL_ROUNDS = 4
    }

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