package com.isledeer.soullink.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isledeer.soullink.data.ApiSettings
import com.isledeer.soullink.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设置页状态。
 *
 * 首次从 DataStore 回填一次，之后以本地编辑为准 ——
 * 否则用户正在输入时，DataStore 的任何一次重发都会把输入框内容冲掉。
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ApiSettings())
    val state: StateFlow<ApiSettings> = _state.asStateFlow()

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

    fun update(transform: (ApiSettings) -> ApiSettings) {
        _state.value = transform(_state.value)
    }

    fun save(onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            repository.save(_state.value)
            loaded = true
            onSaved()
        }
    }
}