package com.isledeer.soullink.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isledeer.soullink.data.ApiSettings
import com.isledeer.soullink.data.MessageDao
import com.isledeer.soullink.data.MessageEntity
import com.isledeer.soullink.data.SettingsRepository
import com.isledeer.soullink.net.ChatRequest
import com.isledeer.soullink.net.ChatTurn
import com.isledeer.soullink.net.SseClient
import com.isledeer.soullink.net.SseEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 聊天页状态机。
 *
 * 三份 StateFlow：消息列表 / 生成中 / API 配置。
 * 职责边界：
 *   - 这里只管「怎么把一次对话跑完并落盘」，不碰任何 Compose
 *   - 渲染层只 collect，不直接改状态
 *
 * 落盘策略：user 消息发出即写；assistant 消息在流结束（含失败/停止）时写一次。
 * 流式过程中不逐字写库——一次对话几十次 IO 没有意义。
 */
class ChatViewModel(
    private val sse: SseClient,
    private val settingsRepository: SettingsRepository,
    private val dao: MessageDao,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _settings = MutableStateFlow(ApiSettings())
    val settings: StateFlow<ApiSettings> = _settings.asStateFlow()

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.flow.collect { _settings.value = it }
        }
        viewModelScope.launch {
            // 只在首次启动、列表还空的时候回填历史，避免覆盖用户刚发的消息
            val history = dao.loadConversation()
            if (history.isNotEmpty() && _messages.value.isEmpty()) {
                _messages.value = history.map { it.toUi() }
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return

        val config = _settings.value
        if (!config.isConfigured) {
            _messages.update {
                it + ChatMessage(
                    id = newId(),
                    role = Role.Assistant,
                    text = "还没配置 API。点右上角 ⚙ 填 Base URL、Key 和模型名。",
                    isError = true,
                )
            }
            return
        }

        val userMessage = ChatMessage(newId(), Role.User, trimmed)
        val assistantId = newId()
        val placeholder = ChatMessage(assistantId, Role.Assistant, "", isStreaming = true)

        _messages.update { it + userMessage + placeholder }
        persist(userMessage)
        _isGenerating.value = true

        // 发给模型的历史：去掉占位消息、错误文案与空内容
        val turns = _messages.value
            .filter { !it.isStreaming && it.text.isNotBlank() && !it.isError }
            .map { ChatTurn(it.role.apiName, it.text) }

        streamJob = viewModelScope.launch {
            try {
                sse.stream(
                    ChatRequest(
                        baseUrl = config.baseUrl,
                        apiKey = config.apiKey,
                        model = config.model,
                        turns = turns,
                        maxTokens = config.maxTokens,
                        temperature = config.temperature,
                    )
                ).collect { event ->
                    when (event) {
                        is SseEvent.Content -> updateAssistant(assistantId) {
                            it.copy(text = it.text + event.text)
                        }

                        is SseEvent.Reasoning -> updateAssistant(assistantId) {
                            it.copy(reasoning = (it.reasoning ?: "") + event.text)
                        }

                        SseEvent.Done -> Unit
                    }
                }
            } catch (e: CancellationException) {
                // 用户点了停止：保留已生成内容，不算错误
            } catch (e: Exception) {
                val reason = e.message ?: "未知错误"
                updateAssistant(assistantId) {
                    it.copy(
                        text = it.text.ifBlank { "请求失败：$reason" },
                        isError = true,
                    )
                }
            } finally {
                updateAssistant(assistantId) { it.copy(isStreaming = false) }
                _isGenerating.value = false
                _messages.value.firstOrNull { it.id == assistantId }?.let { persist(it) }
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
    }

    fun clear() {
        stop()
        viewModelScope.launch {
            dao.clearConversation()
            _messages.value = emptyList()
        }
    }

    /* ------------------------- 内部工具 ------------------------- */

    private fun updateAssistant(id: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.update { list ->
            list.map { if (it.id == id) transform(it) else it }
        }
    }

    private fun persist(message: ChatMessage) {
        viewModelScope.launch { dao.upsert(message.toEntity()) }
    }

    private fun newId(): String =
        "${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}"

    private fun MessageEntity.toUi(): ChatMessage = ChatMessage(
        id = id,
        role = Role.fromApi(role),
        text = content,
        reasoning = reasoning,
        createdAt = createdAt,
    )

    private fun ChatMessage.toEntity(): MessageEntity = MessageEntity(
        id = id,
        role = role.apiName,
        content = text,
        reasoning = reasoning,
        createdAt = createdAt,
    )
}