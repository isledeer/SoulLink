package com.isledeer.soullink.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isledeer.soullink.data.AppSettings
import com.isledeer.soullink.data.AssistantDao
import com.isledeer.soullink.data.AssistantEntity
import com.isledeer.soullink.data.ProviderConfig
import com.isledeer.soullink.data.SettingsRepository
import com.isledeer.soullink.net.SseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 助手管理（列表 + 详情共用）。
 *
 * 列表页负责增删克隆；详情页载入单条到 `detail` 并在本地编辑，点保存才落库。
 */
class AssistantViewModel(
    private val dao: AssistantDao,
    private val sse: SseClient,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch { settingsRepository.flow.collect { _settings.value = it } }
    }

    /** 模型拉取（供 ModelPickerSheet 调用） */
    suspend fun fetchModels(provider: ProviderConfig): Result<List<String>> =
        runCatching { sse.fetchModels(provider.baseUrl, provider.apiKey) }

    private val _assistants = MutableStateFlow<List<AssistantEntity>>(emptyList())
    val assistants: StateFlow<List<AssistantEntity>> = _assistants.asStateFlow()

    private val _detail = MutableStateFlow<AssistantEntity?>(null)
    val detail: StateFlow<AssistantEntity?> = _detail.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _assistants.value = dao.loadAll() }
    }

    fun search(text: String) {
        _query.value = text
    }

    /** 新建一个空白助手，返回 id（详情页要用它导航） */
    fun create(): String {
        val id = UUID.randomUUID().toString()
        val entity = AssistantEntity(
            id = id,
            name = "新助手",
            avatar = "✳️",
            systemPrompt = "你是 SoulLink 的助手。",
        )
        viewModelScope.launch {
            dao.upsert(entity)
            refresh()
        }
        return id
    }

    fun clone(source: AssistantEntity) {
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} 副本",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            dao.upsert(copy)
            refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            dao.delete(id)
            refresh()
        }
    }

    fun load(id: String) {
        viewModelScope.launch { _detail.value = dao.findById(id) }
    }

    fun update(transform: (AssistantEntity) -> AssistantEntity) {
        val current = _detail.value ?: return
        _detail.value = transform(current)
    }

    fun save(onSaved: () -> Unit = {}) {
        val current = _detail.value ?: return
        viewModelScope.launch {
            dao.upsert(current.copy(updatedAt = System.currentTimeMillis()))
            refresh()
            onSaved()
        }
    }
}