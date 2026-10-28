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
import com.isledeer.soullink.data.GameAction
import com.isledeer.soullink.data.GameActionParser
import com.isledeer.soullink.data.GameLedgerRepository
import com.isledeer.soullink.data.GameSessionEntity
import com.isledeer.soullink.data.GameState
import com.isledeer.soullink.data.MessageParts
import com.isledeer.soullink.data.MessageTreeRepository
import com.isledeer.soullink.data.McpServerConfig
import com.isledeer.soullink.data.ProviderConfig
import com.isledeer.soullink.data.ResolvedConfig
import com.isledeer.soullink.data.SettingsRepository
import com.isledeer.soullink.data.SkillsRegistry
import com.isledeer.soullink.data.UIMessageEntity
import com.isledeer.soullink.data.WorldBookDao
import com.isledeer.soullink.data.WorldDoc
import com.isledeer.soullink.data.WorldNpc
import com.isledeer.soullink.data.WorldbookPrompt
import com.isledeer.soullink.data.isGameEnabled
import com.isledeer.soullink.data.parseMcpServers
import com.isledeer.soullink.net.ChatRequest
import com.isledeer.soullink.net.ChatTurn
import com.isledeer.soullink.net.McpClient
import com.isledeer.soullink.net.McpToolInfo
import com.isledeer.soullink.net.SearchService
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
    private val ledger: GameLedgerRepository,
    private val speaker: Speaker,
    private val searchService: SearchService,
    private val worldBookDao: WorldBookDao,
    private val mcpClient: McpClient,
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

    /** 当前会话的账本投影；null = 普通聊天（无游戏层） */
    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    /** 本轮生成是否处于游戏模式（由会话的 GameSession 决定） */
    private var gameActive = false
    private var activeSession: GameSessionEntity? = null
    private val pendingGameActions = mutableListOf<GameAction>()

    /** MCP：serverId -> (缓存时间, 工具列表)；exposedName -> (server, tool) */
    private val mcpTools = mutableMapOf<String, Pair<Long, List<McpToolInfo>>>()
    private val mcpToolIndex = mutableMapOf<String, Pair<McpServerConfig, McpToolInfo>>()

    /** 记忆摘要：conversationId -> (覆盖标记, 摘要文本)；标记 "manual" = /compact 手动压缩 */
    private val memoryCache = mutableMapOf<String, Pair<String, String>>()

    private val _memorySummary = MutableStateFlow<String?>(null)
    val memorySummary: StateFlow<String?> = _memorySummary.asStateFlow()

    /** 游戏会话：同 session 下的全部对话线（世界线 + NPC 线）与可进入的 NPC */
    private val _sessionThreads = MutableStateFlow<List<Conversation>>(emptyList())
    val sessionThreads: StateFlow<List<Conversation>> = _sessionThreads.asStateFlow()

    private val _worldNpcs = MutableStateFlow<List<WorldNpc>>(emptyList())
    val worldNpcs: StateFlow<List<WorldNpc>> = _worldNpcs.asStateFlow()

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
        val global = _settings.value.bindings[Feature.CHAT]?.model
        _currentModelName.value = assistant?.chatModelId?.takeIf { it.isNotBlank() } ?: global?.takeIf { it.isNotBlank() }
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
                // 生图独立 Provider：优先 IMAGE 绑定，回落到 CHAT
                val provider = settings.providerFor(Feature.IMAGE) ?: settings.providerFor(Feature.CHAT)
                val imageModel = settings.bindings[Feature.IMAGE]?.model
                    ?.takeIf { it.isNotBlank() }
                    ?: "dall-e-3"
                tools.imageGenerator = if (provider != null && provider.baseUrl.isNotBlank()) {
                    { prompt -> runBlocking { sse.generateImage(provider.baseUrl, provider.apiKey, prompt, imageModel) } }
                } else {
                    null
                }
                tools.speaker = { text -> speaker.speak(text) }
                speaker.configure(settings.ttsRate, settings.ttsPitch)
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
        refreshGameState(list.first().id)
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
            _gameState.value = null
            _memorySummary.value = null
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
            refreshGameState(id)
            _memorySummary.value = memoryCache[id]?.takeIf { it.first == "manual" }?.second
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationDao.delete(id)
            tree.clear(id)
            ledger.deleteForConversation(id)
            memoryCache.remove(id)
            val remaining = conversationDao.loadAll()
            if (remaining.isEmpty()) {
                val fresh = newConversationEntity(_assistants.value.firstOrNull())
                conversationDao.upsert(fresh)
                _conversations.value = listOf(fresh.toUi())
                _current.value = fresh.toUi()
                _messages.value = emptyList()
                _gameState.value = null
            } else {
                _conversations.value = remaining.map { it.toUi() }
                if (_current.value?.id == id) {
                    _current.value = remaining.first().toUi()
                    reloadMessages(remaining.first().id)
                    refreshGameState(remaining.first().id)
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
        val conversationId = _current.value?.id
        viewModelScope.launch {
            tree.deleteNode(message.nodeId)
            ledger.deleteForNode(message.nodeId)
            _messages.update { list -> list.filterNot { it.id == id } }
            if (conversationId != null) refreshGameState(conversationId)
        }
    }

    fun editMessage(id: String, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return
        val message = _messages.value.firstOrNull { it.id == id } ?: return
        val conversationId = _current.value?.id ?: return
        viewModelScope.launch {
            var storedText = trimmed
            var extractedAction: GameAction? = null
            val session = if (message.role == Role.Assistant) {
                runCatching { ledger.sessionOf(conversationId) }.getOrNull()
            } else {
                null
            }
            if (session?.isGameEnabled == true) {
                val extracted = GameActionParser.extract(trimmed)
                storedText = extracted.first
                extractedAction = extracted.second
            }
            tree.updateMessage(
                UIMessageEntity(
                    id = message.id,
                    nodeId = message.nodeId,
                    role = message.role.apiName,
                    parts = MessageParts.encode(storedText, message.imageBase64, message.choices),
                    reasoning = message.reasoning,
                    usageTokens = message.tokenCount,
                    generationSpeed = message.generationSpeed,
                    isFavorite = message.isFavorite,
                    createdAt = message.createdAt,
                )
            )
            _messages.update { list ->
                list.map { if (it.id == id) it.copy(text = storedText) else it }
            }
            if (session != null && session.isGameEnabled) {
                val state = ledger.replaceActions(
                    session = session,
                    conversationId = conversationId,
                    messageId = message.id,
                    nodeId = message.nodeId,
                    actions = listOfNotNull(extractedAction),
                    source = "BLOCK",
                )
                _gameState.value = state
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
                    parts = MessageParts.encode(message.text, message.imageBase64, message.choices),
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
                    parts = MessageParts.encode(msg.text, msg.imageBase64, msg.choices),
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
            ledger.deleteForConversation(conversation.id)
            _messages.value = emptyList()
            _tokenUsage.value = 0
            refreshGameState(conversation.id)
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

    /** 切换当前助手启用的 MCP 服务器并落库 */
    fun toggleMcpServer(id: String) {
        val assistant = _currentAssistant.value ?: return
        val list = assistant.mcpList().toMutableList()
        if (list.contains(id)) list.remove(id) else list.add(id)
        val array = org.json.JSONArray()
        list.forEach { array.put(it) }
        val updated = assistant.copy(mcpServers = array.toString(), updatedAt = System.currentTimeMillis())
        _currentAssistant.value = updated
        viewModelScope.launch { assistantDao.upsert(updated) }
    }

    /** 切换当前助手启用的技能并落库 */
    fun toggleSkill(id: String) {
        val assistant = _currentAssistant.value ?: return
        val list = assistant.skillList().toMutableList()
        if (list.contains(id)) list.remove(id) else list.add(id)
        val array = org.json.JSONArray()
        list.forEach { array.put(it) }
        val updated = assistant.copy(enabledSkills = array.toString(), updatedAt = System.currentTimeMillis())
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

        // 游戏模式判定：会话绑定了游戏存档且已启用
        activeSession = runCatching { ledger.sessionOf(conversation.id) }.getOrNull()
        gameActive = activeSession?.isGameEnabled == true
        pendingGameActions.clear()

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

        // 2) 组请求（记忆 / 联网搜索增强段在请求前拼好，不污染消息树）
        val usable = _messages.value.filter {
            !it.isStreaming && !it.isError && (it.text.isNotBlank() || it.imageBase64 != null)
        }
        val finalSystemPrompt = buildEnhancedPrompt(conversation, config, usable)
        val turns = buildTurns(config)
        val mcpSchemas = prepareMcpTools()
        val startedAt = SystemClock.elapsedRealtime()
        var usageTokens = 0
        var receivedChars = 0

        streamJob = viewModelScope.launch {
            var finalText = ""
            var finalReasoning = ""
            var finalImage: String? = null
            var cancelled = false
            var failed = false
            // 流式节流：chunk 先进 StringBuilder，~90ms 才 flush 一次到 StateFlow，
            // 避免每个 token 都触发全列表重组
            val pendingText = StringBuilder()
            var lastFlushAt = 0L

            fun flushPending() {
                if (pendingText.isEmpty()) return
                val chunk = pendingText.toString()
                pendingText.clear()
                finalText += chunk
                receivedChars += chunk.length
                patchMessage(messageId) { it.copy(text = finalText) }
            }

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
                            systemPrompt = finalSystemPrompt,
                            maxTokens = config.maxTokens,
                            temperature = config.temperature,
                            topP = config.topP,
                            toolSchemas = toolSchemas(config.tools) + mcpSchemas,
                            thinking = _thinkingLevel.value,
                            extraHeaders = config.headers,
                            extraBody = config.bodyExtras,
                        )
                    ).collect { event ->
                        when (event) {
                            is SseEvent.Content -> {
                                pendingText.append(event.text)
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastFlushAt >= STREAM_FLUSH_MS) {
                                    lastFlushAt = now
                                    flushPending()
                                }
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

                    // 每轮结束 flush 残段（保证不足 90ms 的尾巴也显示）
                    flushPending()

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
                // 用户停止：保留已生成，不落账
                cancelled = true
            } catch (e: Exception) {
                failed = true
                flushPending()
                val reason = e.message ?: "未知错误"
                if (finalText.isBlank()) finalText = "请求失败：$reason"
                patchMessage(messageId) { it.copy(text = finalText, isError = true) }
            } finally {
                val elapsed = ((SystemClock.elapsedRealtime() - startedAt) / 1000.0).coerceAtLeast(0.001)
                val tokens = if (usageTokens > 0) usageTokens else (receivedChars / 2).coerceAtLeast(0)
                val speed = if (tokens > 0) tokens / elapsed else 0.0

                // 剥 <game> 尾块（游戏模式才做；普通聊天不动正文）
                var visibleText = finalText
                var blockAction: GameAction? = null
                if (gameActive) {
                    val extracted = GameActionParser.extract(finalText)
                    visibleText = extracted.first
                    blockAction = extracted.second
                }

                val choiceAction = pendingGameActions.lastOrNull { it.choices.isNotEmpty() }
                    ?: blockAction?.takeIf { it.choices.isNotEmpty() }
                val choices = choiceAction?.choices.orEmpty()

                tree.updateMessage(
                    UIMessageEntity(
                        id = messageId,
                        nodeId = nodeId,
                        role = Role.Assistant.apiName,
                        parts = MessageParts.encode(visibleText, finalImage, choices),
                        reasoning = finalReasoning.ifBlank { null },
                        usageTokens = tokens,
                        generationSpeed = speed,
                        createdAt = System.currentTimeMillis(),
                    )
                )

                // 账本落事件：取消/报错不写（避免半截状态）；工具通道优先于 <game> 块
                if (gameActive && !cancelled && !failed) {
                    val session = activeSession
                    if (session != null) {
                        try {
                            val actions = mutableListOf<GameAction>()
                            actions.addAll(pendingGameActions)
                            if (actions.isEmpty()) {
                                blockAction?.let { action ->
                                    if (!action.isEmpty) actions.add(action)
                                }
                            }
                            val source = if (pendingGameActions.isNotEmpty()) "TOOL" else "BLOCK"
                            val state = ledger.replaceActions(
                                session = session,
                                conversationId = conversation.id,
                                messageId = messageId,
                                nodeId = nodeId,
                                actions = actions,
                                source = source,
                            )
                            _gameState.value = state
                        } catch (e: Exception) {
                            // 账本写入失败不影响消息本身
                        }
                    }
                }
                pendingGameActions.clear()

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
        val turns = limited.map { msg ->
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
        // 游戏模式且没启用 game 工具：块通道指令贴最后一条 user 消息（旧引擎教训）
        if (!gameActive) return turns
        if (config.tools.any { it.name == GAME_TOOL_NAME }) return turns
        val lastUser = turns.indexOfLast { it.role == "user" }
        if (lastUser < 0) return turns
        val updated = turns.toMutableList()
        val target = updated[lastUser]
        updated[lastUser] = target.copy(content = target.content + "\n\n" + GAME_BLOCK_SUFFIX)
        return updated
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
                choices = MessageParts.choices(turn.message.parts),
                createdAt = turn.message.createdAt,
            )
        }
        _tokenUsage.value = _messages.value.sumOf { it.tokenCount }
    }

    /** 重新推导当前会话的账本状态；普通聊天置 null。 */
    private suspend fun refreshGameState(conversationId: String) {
        val session = runCatching { ledger.sessionOf(conversationId) }.getOrNull()
        if (session == null || !session.isGameEnabled) {
            _gameState.value = null
            _sessionThreads.value = emptyList()
            _worldNpcs.value = emptyList()
            return
        }
        _gameState.value = runCatching { ledger.derive(session.id) }.getOrNull()
        _sessionThreads.value = runCatching {
            conversationDao.loadBySession(session.id).map { it.toUi() }
        }.getOrDefault(emptyList())
        val worldbookId = session.worldbookId
        _worldNpcs.value = if (!worldbookId.isNullOrBlank()) {
            runCatching {
                WorldDoc.parse(worldBookDao.findById(worldbookId)?.docJson).npcs
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    /** 进入 NPC 攻略线（同一本账的独立会话；已存在则直接切换） */
    fun startNpcThread(npcId: String) {
        val conv = _current.value ?: return
        viewModelScope.launch {
            val session = ledger.sessionOf(conv.id) ?: return@launch
            val worldbookId = session.worldbookId ?: return@launch
            val book = worldBookDao.findById(worldbookId) ?: return@launch
            val doc = WorldDoc.parse(book.docJson)
            val npc = doc.npcs.firstOrNull { it.id == npcId || it.name == npcId } ?: return@launch

            val existing = conversationDao.loadBySession(session.id).firstOrNull { it.title == npc.name }
            if (existing != null) {
                selectConversation(existing.id)
                return@launch
            }

            val now = System.currentTimeMillis()
            val assistantId = java.util.UUID.randomUUID().toString()
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
            val conversationId = java.util.UUID.randomUUID().toString()
            conversationDao.upsert(
                ConversationEntity(
                    id = conversationId,
                    title = npc.name,
                    assistantId = assistantId,
                    sessionId = session.id,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            refreshConversations()
            selectConversation(conversationId)
        }
    }

    /** 从数据库重载会话列表（世界书「开始游戏」后回聊天页会用到） */
    fun refreshConversations() {
        viewModelScope.launch {
            _conversations.value = conversationDao.loadAll().map { it.toUi() }
        }
    }

    /** 拉取 MCP 工具（5 分钟缓存）并转成 OpenAI function schema */
    private suspend fun prepareMcpTools(): List<JSONObject> {
        val assistant = _currentAssistant.value ?: return emptyList()
        val enabledIds = assistant.mcpList()
        if (enabledIds.isEmpty()) return emptyList()

        val servers = parseMcpServers(_settings.value.mcpServersJson)
            .filter { it.id in enabledIds && it.url.isNotBlank() }
        mcpToolIndex.clear()
        val schemas = mutableListOf<JSONObject>()
        val now = System.currentTimeMillis()

        for (server in servers) {
            val cached = mcpTools[server.id]
            val tools = if (cached != null && now - cached.first < 5 * 60 * 1000L) {
                cached.second
            } else {
                val fetched = mcpClient.listTools(server).getOrNull() ?: continue
                mcpTools[server.id] = now to fetched
                fetched
            }
            tools.forEach { tool ->
                val exposed = "mcp_" + server.id.take(6) + "_" + tool.name
                mcpToolIndex[exposed] = server to tool
                schemas.add(
                    JSONObject()
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject()
                                .put("name", exposed)
                                .put("description", "[MCP·${server.name}] ${tool.description}".take(400))
                                .put("parameters", tool.inputSchema)
                        )
                )
            }
        }
        return schemas
    }

    /** 记忆 + 联网搜索增强段（请求前拼好，不污染消息树） */
    private suspend fun buildEnhancedPrompt(
        conversation: Conversation,
        config: ResolvedConfig,
        usable: List<ChatMessage>,
    ): String {
        val sections = mutableListOf<String>()

        // 世界书模式：世界规则 + 激活条目 + 状态快照 + 输出契约放最前
        val session = activeSession
        val worldbookId = session?.worldbookId
        if (gameActive && !worldbookId.isNullOrBlank()) {
            val book = runCatching { worldBookDao.findById(worldbookId) }.getOrNull()
            if (book != null) {
                val doc = WorldDoc.parse(book.docJson)
                val entries = runCatching { worldBookDao.entriesOf(worldbookId) }.getOrDefault(emptyList())
                val recent = usable.takeLast(6).joinToString("\n") { it.text }
                val activeEntries = WorldbookPrompt.activate(entries, recent)
                val useTool = config.tools.any { it.name == GAME_TOOL_NAME }
                sections.add(WorldbookPrompt.buildSystemSection(doc, activeEntries, _gameState.value, useTool))
            }
        }

        config.systemPrompt.takeIf { it.isNotBlank() }?.let { sections.add(it) }

        val assistant = _currentAssistant.value

        // 助手技能：勾选的技能指令注入
        val skillIds = assistant?.skillList().orEmpty()
        SkillsRegistry.buildSection(skillIds).takeIf { it.isNotBlank() }?.let { sections.add(it) }

        val compressOn = _autoCompress.value || assistant?.enableMemory == true
        if (compressOn && usable.size > MEMORY_THRESHOLD) {
            memorySection(conversation, config, usable)?.let { sections.add(it) }
        }
        if (isSearchEnabled.value) {
            val query = usable.lastOrNull { it.role == Role.User }?.text
            if (!query.isNullOrBlank()) {
                searchSection(query)?.let { sections.add(it) }
            }
        }
        return sections.joinToString("\n\n")
    }

    /** 记忆段：/compact 的会话记忆 + 超长上下文自动摘要（带缓存） */
    private suspend fun memorySection(
        conversation: Conversation,
        config: ResolvedConfig,
        usable: List<ChatMessage>,
    ): String? {
        val parts = mutableListOf<String>()
        memoryCache[conversation.id]?.takeIf { it.first == "manual" }?.second?.let {
            parts.add("【会话记忆】\n$it")
        }

        val old = usable.dropLast(KEEP_RECENT)
        if (old.isNotEmpty() && config.isConfigured) {
            val upTo = old.last().id
            val cached = memoryCache[conversation.id]
            if (cached != null && cached.first == upTo && cached.second.isNotBlank()) {
                parts.add("【历史记忆】\n${cached.second}")
            } else {
                val turns = old.map {
                    ChatTurn(role = it.role.apiName, content = it.text, imageBase64 = it.imageBase64)
                }
                val summary = runCatching {
                    sse.complete(
                        ChatRequest(
                            baseUrl = config.provider?.baseUrl.orEmpty(),
                            apiKey = config.provider?.apiKey.orEmpty(),
                            model = config.model,
                            turns = turns,
                            systemPrompt = "你是对话摘要器。把对话压缩成要点记忆，保留人物、事件、约定、用户偏好，不超过 300 字，直接输出要点。",
                            maxTokens = 600,
                            temperature = 0.3,
                        )
                    )
                }.getOrNull()?.trim()
                if (!summary.isNullOrBlank()) {
                    memoryCache[conversation.id] = upTo to summary
                    _memorySummary.value = summary
                    parts.add("【历史记忆】\n$summary")
                }
            }
        }
        return parts.joinToString("\n\n").ifBlank { null }
    }

    /** 联网搜索：按启用的引擎逐个尝试，第一个成功的返回结果段 */
    private suspend fun searchSection(query: String): String? {
        val keys = _settings.value.searchKeys
        for ((engine, _) in SearchService.ENGINES) {
            if (_searchEngines.value[engine] != true) continue
            val key = keys[engine]
            if (key.isNullOrBlank()) continue
            val result = runCatching { searchService.search(engine, key, query) }.getOrNull()
            if (!result.isNullOrBlank()) {
                return "【联网搜索结果】\n$result"
            }
        }
        return null
    }

    /** /compact：压缩当前上下文为会话记忆，清空历史；摘要自动注入后续请求 */
    fun compactNow(onDone: (String) -> Unit = {}) {
        val conv = _current.value ?: return
        if (_isGenerating.value) return
        viewModelScope.launch {
            val assistant = _currentAssistant.value ?: _assistants.value.firstOrNull()
            val config = ConfigResolver.resolve(_settings.value, assistant, conv.toEntity(), tools)
            val usable = _messages.value.filter {
                !it.isStreaming && !it.isError && (it.text.isNotBlank() || it.imageBase64 != null)
            }
            var summary: String? = null
            if (config.isConfigured && usable.isNotEmpty()) {
                val turns = usable.map {
                    ChatTurn(role = it.role.apiName, content = it.text, imageBase64 = it.imageBase64)
                }
                summary = runCatching {
                    sse.complete(
                        ChatRequest(
                            baseUrl = config.provider?.baseUrl.orEmpty(),
                            apiKey = config.provider?.apiKey.orEmpty(),
                            model = config.model,
                            turns = turns,
                            systemPrompt = "你是对话摘要器。把对话压缩成要点记忆，保留人物、事件、约定、用户偏好，不超过 300 字，直接输出要点。",
                            maxTokens = 600,
                            temperature = 0.3,
                        )
                    )
                }.getOrNull()?.trim()
            }
            val text = summary
            if (!text.isNullOrBlank()) {
                memoryCache[conv.id] = "manual" to text
                _memorySummary.value = text
            }
            tree.clear(conv.id)
            ledger.deleteForConversation(conv.id)
            _messages.value = emptyList()
            _tokenUsage.value = 0
            refreshGameState(conv.id)
            onDone(text ?: "上下文已清空")
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

    /**
     * 执行一次工具调用，返回回填给 API 的 tool turn。
     * 生图类工具：base64 写进消息图片区，tool 回填内容用小文本。
     */
    private suspend fun executeToolCall(
        messageId: String,
        call: SseEvent.ToolCall,
    ): ToolTurnResult {
        // 游戏状态工具不走通用执行器：参数暂存，消息定稿时统一落账
        if (call.name == GAME_TOOL_NAME) {
            if (!gameActive) {
                return ToolTurnResult(call.id, "游戏状态未启用", null)
            }
            val action = runCatching { GameAction.fromJson(JSONObject(call.arguments)) }.getOrNull()
            if (action == null) {
                return ToolTurnResult(call.id, "参数无法解析", null)
            }
            if (!action.isEmpty) pendingGameActions.add(action)
            return ToolTurnResult(call.id, "已记录", null)
        }

        // MCP 工具：按暴露名路由到对应服务器
        val mcpEntry = mcpToolIndex[call.name]
        if (mcpEntry != null) {
            val args = runCatching { JSONObject(call.arguments) }.getOrDefault(JSONObject())
            val result = mcpClient.callTool(mcpEntry.first, mcpEntry.second.name, args).getOrNull()
            return ToolTurnResult(call.id, result ?: "MCP 工具调用失败", null)
        }

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

        /** 超过 12 条消息触发自动摘要，保留最近 8 条原文 */
        /** 流式 UI 刷新间隔：chunk 累积到该间隔才写一次 StateFlow */
        private const val STREAM_FLUSH_MS = 90L

        private const val MEMORY_THRESHOLD = 12
        private const val KEEP_RECENT = 8

        private const val GAME_TOOL_NAME = "update_game_state"

        /** 块通道的 game 指令（贴最后一条 user 消息末尾） */
        private const val GAME_BLOCK_SUFFIX =
            "请在回复正文结束后，另起一行输出：\n" +
                "<game>{\"relation\":{\"target\":\"self\",\"delta\":0},\"choices\":[\"选项一\",\"选项二\",\"选项三\",\"选项四\"]}</game>\n" +
                "（delta 为 -10 到 10 的整数，无变化填 0；choices 给四个用户此刻能做的具体动作。不要在正文里提到这个标记。）"
    }

    private fun ConversationEntity.toUi() = Conversation(
        id = id,
        title = title,
        assistantId = assistantId,
        sessionId = sessionId,
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
        sessionId = sessionId,
        systemPrompt = systemPrompt,
        maxTokens = maxTokens,
        temperature = temperature,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}