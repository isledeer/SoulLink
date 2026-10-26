package com.isledeer.soullink.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isledeer.soullink.data.AppSettings
import com.isledeer.soullink.data.ConversationDao
import com.isledeer.soullink.data.ConversationEntity
import com.isledeer.soullink.data.Feature
import com.isledeer.soullink.data.MessageDao
import com.isledeer.soullink.data.MessageEntity
import com.isledeer.soullink.data.SettingsRepository
import com.isledeer.soullink.net.ChatRequest
import com.isledeer.soullink.net.ChatTurn
import com.isledeer.soullink.net.SseClient
import com.isledeer.soullink.net.SseEvent
import com.isledeer.soullink.util.Speaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 全局聊天状态机（会话 + 消息 + 流式 + 统计）。
 *
 * 状态一律单向：UI collect，ViewModel 改。
 * 落盘策略：user 消息立即写；assistant 消息在流结束（成功/失败/停止）时写一次。
 */
class ChatViewModel(
    private val sse: SseClient,
    private val settingsRepository: SettingsRepository,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val speaker: Speaker,
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _current = MutableStateFlow<Conversation?>(null)
    val current: StateFlow<Conversation?> = _current.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.flow.collect { _settings.value = it }
        }
        viewModelScope.launch {
            val stored = conversationDao.loadAll()
            val list = if (stored.isEmpty()) {
                val fresh = newConversationEntity()
                conversationDao.upsert(fresh)
                listOf(fresh)
            } else {
                stored
            }
            _conversations.value = list.map { it.toUi() }
            _current.value = list.first().toUi()
            loadMessages(conversationId = list.first().id)
        }
    }

    /* ------------------------------ 会话 ------------------------------ */

    fun newConversation() {
        stop()
        viewModelScope.launch {
            val entity = newConversationEntity()
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
            loadMessages(id)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationDao.delete(id)
            messageDao.clearConversation(id)
            val remaining = conversationDao.loadAll()
            if (remaining.isEmpty()) {
                val fresh = newConversationEntity()
                conversationDao.upsert(fresh)
                _conversations.value = listOf(fresh.toUi())
                _current.value = fresh.toUi()
                _messages.value = emptyList()
            } else {
                _conversations.value = remaining.map { it.toUi() }
                if (_current.value?.id == id) {
                    _current.value = remaining.first().toUi()
                    loadMessages(remaining.first().id)
                }
            }
        }
    }

    /** 更新当前会话的 System Prompt / 采样参数 */
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
            _conversations.update { list ->
                list.map { if (it.id == updated.id) updated else it }
            }
        }
    }

    /** 从某条消息处分叉出一个新会话（复制该条及之前的全部内容） */
    fun branchFrom(messageId: String) {
        val source = _current.value ?: return
        val list = _messages.value
        val index = list.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val slice = list.take(index + 1)

        viewModelScope.launch {
            val entity = ConversationEntity(
                id = newId(),
                title = "${source.title} · 分支",
                systemPrompt = source.systemPrompt,
                maxTokens = source.maxTokens,
                temperature = source.temperature,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            conversationDao.upsert(entity)
            slice.forEach { msg ->
                messageDao.upsert(
                    msg.copy(id = newId()).toEntity(entity.id)
                )
            }
            val reloaded = messageDao.loadConversation(entity.id)
            val all = conversationDao.loadAll()
            _conversations.value = all.map { it.toUi() }
            _current.value = entity.toUi()
            _messages.value = reloaded.map { it.toUi() }
        }
    }

    /* ------------------------------ 消息 ------------------------------ */

    fun send(text: String, imageBase64: String? = null) {
        val trimmed = text.trim()
        if ((trimmed.isEmpty() && imageBase64 == null) || _isGenerating.value) return
        val conversation = _current.value ?: return

        val userMessage = ChatMessage(
            id = newId(),
            role = Role.User,
            text = trimmed,
            imageBase64 = imageBase64,
        )
        val assistantId = newId()

        _messages.update {
            it + userMessage + ChatMessage(assistantId, Role.Assistant, "", isStreaming = true)
        }
        persist(userMessage, conversation.id)
        _isGenerating.value = true
        launchStream(assistantId, conversation)
    }

    /** 重新生成：丢掉该条 assistant 及其后所有内容，再跑一次 */
    fun regenerate(messageId: String) {
        if (_isGenerating.value) return
        val conversation = _current.value ?: return
        val list = _messages.value
        val index = list.indexOfFirst { it.id == messageId }
        if (index < 0) return

        val target = list[index]
        val kept = if (target.role == Role.Assistant) list.take(index) else list.take(index + 1)
        val dropped = list.drop(kept.size)

        viewModelScope.launch { dropped.forEach { messageDao.deleteById(it.id) } }

        val assistantId = newId()
        _messages.value = kept + ChatMessage(assistantId, Role.Assistant, "", isStreaming = true)
        _isGenerating.value = true
        launchStream(assistantId, conversation)
    }

    fun deleteMessage(id: String) {
        viewModelScope.launch { messageDao.deleteById(id) }
        _messages.update { list -> list.filterNot { it.id == id } }
    }

    fun editMessage(id: String, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return
        _messages.update { list ->
            list.map { if (it.id == id) it.copy(text = trimmed) else it }
        }
        val conversationId = _current.value?.id ?: return
        _messages.value.firstOrNull { it.id == id }?.let { persist(it, conversationId) }
    }

    fun toggleFavorite(id: String) {
        val message = _messages.value.firstOrNull { it.id == id } ?: return
        val next = message.copy(isFavorite = !message.isFavorite)
        _messages.update { list -> list.map { if (it.id == id) next else it } }
        viewModelScope.launch { messageDao.setFavorite(id, next.isFavorite) }
    }

    fun speak(text: String) = speaker.speak(text)

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        _isGenerating.value = false
    }

    fun clearCurrent() {
        val conversation = _current.value ?: return
        stop()
        viewModelScope.launch {
            messageDao.clearConversation(conversation.id)
            _messages.value = emptyList()
        }
    }

    /* ---------------------------- 内部实现 ---------------------------- */

    private fun launchStream(assistantId: String, conversation: Conversation) {
        val config = _settings.value
        val provider = config.providerFor(Feature.CHAT)
        val model = config.modelFor(Feature.CHAT)

        if (provider == null || provider.baseUrl.isBlank() || provider.apiKey.isBlank()) {
            failAssistant(assistantId, "还没配置 Provider。点右上角 ⚙ 添加 Base URL 与 Key。")
            return
        }

        val turns = _messages.value
            .filter { !it.isStreaming && !it.isError && (it.text.isNotBlank() || it.imageBase64 != null) }
            .map { ChatTurn(it.role.apiName, it.text, it.imageBase64) }

        val startedAt = SystemClock.elapsedRealtime()
        var usageTokens = 0
        var receivedChars = 0

        streamJob = viewModelScope.launch {
            try {
                streamWithFallback(
                    base = ChatRequest(
                        baseUrl = provider.baseUrl,
                        apiKey = provider.apiKey,
                        model = model,
                        turns = turns,
                        systemPrompt = conversation.systemPrompt,
                        maxTokens = conversation.maxTokens,
                        temperature = conversation.temperature,
                    ),
                    assistantId = assistantId,
                    onUsage = { usageTokens = it },
                    onChars = { receivedChars += it },
                )
            } catch (e: CancellationException) {
                // 用户停止：保留已生成内容
            } catch (e: Exception) {
                val reason = e.message ?: "未知错误"
                updateAssistant(assistantId) {
                    it.copy(
                        text = it.text.ifBlank { "请求失败：$reason" },
                        isError = it.text.isBlank(),
                    )
                }
            } finally {
                val elapsedSec = ((SystemClock.elapsedRealtime() - startedAt) / 1000.0)
                    .coerceAtLeast(0.001)
                val tokens = if (usageTokens > 0) usageTokens else estimateTokens(receivedChars)
                val speed = if (tokens > 0) tokens / elapsedSec else 0.0

                updateAssistant(assistantId) {
                    it.copy(
                        isStreaming = false,
                        tokenCount = tokens,
                        generationSpeed = speed,
                    )
                }
                _isGenerating.value = false
                _messages.value.firstOrNull { it.id == assistantId }?.let {
                    persist(it, conversation.id)
                }
                conversationDao.upsert(
                    conversation.copy(updatedAt = System.currentTimeMillis()).toEntity()
                )
                _conversations.update { list ->
                    list.map {
                        if (it.id == conversation.id) it.copy(updatedAt = System.currentTimeMillis()) else it
                    }.sortedByDescending { it.updatedAt }
                }
            }
        }
    }

    /**
     * 带降级的流式收集：
     * 部分网关不认识 stream_options，会直接 400。捕获后去掉该字段重试一次。
     */
    private suspend fun streamWithFallback(
        base: ChatRequest,
        assistantId: String,
        onUsage: (Int) -> Unit,
        onChars: (Int) -> Unit,
    ) {
        try {
            collectStream(base, assistantId, onUsage, onChars)
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            if (base.includeUsage && message.contains("stream_options", ignoreCase = true)) {
                collectStream(base.copy(includeUsage = false), assistantId, onUsage, onChars)
            } else {
                throw e
            }
        }
    }

    private suspend fun collectStream(
        request: ChatRequest,
        assistantId: String,
        onUsage: (Int) -> Unit,
        onChars: (Int) -> Unit,
    ) {
        sse.stream(request).collect { event ->
            when (event) {
                is SseEvent.Content -> {
                    onChars(event.text.length)
                    updateAssistant(assistantId) { it.copy(text = it.text + event.text) }
                }

                is SseEvent.Reasoning -> updateAssistant(assistantId) {
                    it.copy(reasoning = (it.reasoning ?: "") + event.text)
                }

                is SseEvent.Usage -> {
                    if (event.completionTokens > 0) onUsage(event.completionTokens)
                }

                SseEvent.Done -> Unit
            }
        }
    }

    private fun failAssistant(id: String, reason: String) {
        updateAssistant(id) { it.copy(text = reason, isError = true, isStreaming = false) }
        _isGenerating.value = false
    }

    private fun updateAssistant(id: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun estimateTokens(chars: Int): Int = if (chars <= 0) 0 else (chars / 2).coerceAtLeast(1)

    private fun loadMessages(conversationId: String) {
        viewModelScope.launch {
            _messages.value = messageDao.loadConversation(conversationId).map { it.toUi() }
        }
    }

    private fun persist(message: ChatMessage, conversationId: String) {
        viewModelScope.launch { messageDao.upsert(message.toEntity(conversationId)) }
    }

    private fun newConversationEntity(): ConversationEntity {
        val now = System.currentTimeMillis()
        return ConversationEntity(
            id = newId(),
            title = "新对话",
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun newId(): String = "${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}"

    /* ----------------------------- 映射 ----------------------------- */

    private fun ConversationEntity.toUi() = Conversation(
        id = id,
        title = title,
        systemPrompt = systemPrompt,
        maxTokens = maxTokens,
        temperature = temperature,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun Conversation.toEntity() = ConversationEntity(
        id = id,
        title = title,
        systemPrompt = systemPrompt,
        maxTokens = maxTokens,
        temperature = temperature,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun MessageEntity.toUi() = ChatMessage(
        id = id,
        role = Role.fromApi(role),
        text = content,
        reasoning = reasoning,
        imageBase64 = imageBase64,
        isFavorite = isFavorite,
        generationSpeed = generationSpeed,
        tokenCount = tokenCount,
        createdAt = createdAt,
    )

    private fun ChatMessage.toEntity(conversationId: String) = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role.apiName,
        content = text,
        reasoning = reasoning,
        imageBase64 = imageBase64,
        generationSpeed = generationSpeed,
        tokenCount = tokenCount,
        isFavorite = isFavorite,
        createdAt = createdAt,
    )
}