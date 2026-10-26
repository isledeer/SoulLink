package com.isledeer.soullink.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isledeer.soullink.data.AppSettings
import com.isledeer.soullink.data.Feature
import com.isledeer.soullink.data.FeatureBinding
import com.isledeer.soullink.data.ProviderConfig
import com.isledeer.soullink.data.SettingsRepository
import com.isledeer.soullink.net.SseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Provider 管理 + 功能绑定 + 连通性测试。
 *
 * 编辑态与已存态合并成一个 StateFlow：改动即时反映在 UI，点保存才落盘。
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val sse: SseClient,
) : ViewModel() {

    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private val _testing = MutableStateFlow<String?>(null)
    val testing: StateFlow<String?> = _testing.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private val _fetchedModels = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val fetchedModels: StateFlow<Map<String, List<String>>> = _fetchedModels.asStateFlow()

    private val _balanceText = MutableStateFlow<String?>(null)
    val balanceText: StateFlow<String?> = _balanceText.asStateFlow()

    private var loaded = false

    init {
        viewModelScope.launch {
            repository.flow.collect { stored ->
                if (!loaded) {
                    _state.value = stored
                    loaded = true
                }
            }
        }
    }

    fun addProvider() {
        val provider = ProviderConfig(
            id = "p-${System.currentTimeMillis()}-${Random.nextInt(100, 999)}",
            name = "新提供商",
            baseUrl = "",
            apiKey = "",
        )
        _state.value = _state.value.copy(providers = _state.value.providers + provider)
    }

    fun updateProvider(updated: ProviderConfig) {
        _state.value = _state.value.copy(
            providers = _state.value.providers.map { if (it.id == updated.id) updated else it }
        )
    }

    fun removeProvider(id: String) {
        _state.value = _state.value.copy(
            providers = _state.value.providers.filterNot { it.id == id },
            bindings = _state.value.bindings.filterValues { it.providerId != id },
        )
    }

    fun setBinding(feature: Feature, providerId: String, model: String) {
        val bindings = _state.value.bindings.toMutableMap()
        bindings[feature] = FeatureBinding(providerId = providerId, model = model)
        _state.value = _state.value.copy(bindings = bindings)
    }

    fun save() {
        viewModelScope.launch {
            repository.save(_state.value)
            loaded = true
        }
    }

    /** 从 JSON 文本导入配置（对应二维码/文本分享） */
    fun importConfig(raw: String) {
        val parsed = AppSettings.fromJson(raw)
        if (parsed.providers.isEmpty() && raw.isNotBlank()) {
            _testResult.value = "导入失败：不是有效的配置 JSON"
            return
        }
        _state.value = parsed
        viewModelScope.launch {
            repository.save(parsed)
            loaded = true
            _testResult.value = "已导入 ${parsed.providers.size} 个提供商"
        }
    }

    /** 一键拉取：把该 provider 的模型列表存进 fetchedModels */
    fun fetchModelList(provider: ProviderConfig) {
        viewModelScope.launch {
            runCatching { sse.fetchModels(provider.baseUrl, provider.apiKey) }
                .onSuccess { models -> _fetchedModels.value = _fetchedModels.value + (provider.id to models) }
                .onFailure { _testResult.value = "拉取失败：${it.message}" }
        }
    }

    /** 余额查询：GET balanceUrl，按点分路径取值 */
    fun queryBalance(provider: ProviderConfig) {
        if (provider.balanceUrl.isBlank()) {
            _balanceText.value = "未配置余额查询地址"
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                val raw = sse.fetchJson(provider.balanceUrl, provider.apiKey)
                if (provider.balanceJsonPath.isBlank()) raw
                else extractJsonPath(raw, provider.balanceJsonPath) ?: "路径 ${provider.balanceJsonPath} 未命中"
            }
            _balanceText.value = result.getOrElse { "余额查询失败：${it.message}" }
        }
    }

    /** 极简 JSON Path（点分，支持数组 [0]） */
    private fun extractJsonPath(raw: String, path: String): String? = runCatching {
        var current: Any = org.json.JSONObject(raw)
        for (segment in path.split(".")) {
            current = when {
                segment.endsWith("]") && segment.contains("[") -> {
                    val name = segment.substringBefore("[")
                    val index = segment.substringAfter("[").substringBefore("]").toInt()
                    (current as org.json.JSONObject).optJSONArray(name)?.opt(index) ?: return null
                }
                else -> (current as org.json.JSONObject).opt(segment) ?: return null
            }
        }
        current.toString()
    }.getOrNull()

    /** 模型拉取（供 ModelPickerSheet 调用） */
    suspend fun fetchModels(provider: ProviderConfig): Result<List<String>> =
        runCatching { sse.fetchModels(provider.baseUrl, provider.apiKey) }

    fun test(provider: ProviderConfig) {
        if (provider.baseUrl.isBlank()) {
            _testResult.value = "Base URL 为空"
            return
        }
        _testing.value = provider.id
        _testResult.value = null
        viewModelScope.launch {
            val result = runCatching { sse.testConnection(provider.baseUrl, provider.apiKey) }
            _testResult.value = result.getOrElse { "失败：${it.message ?: "未知错误"}" }
            _testing.value = null
        }
    }
}