package com.isledeer.soullink.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * API 配置。刻意只存 OpenAI 兼容端点需要的最小集合：
 * 纯本地应用，不托管、不中转，Key 只在本机 DataStore 里。
 */
data class ApiSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val maxTokens: Int = 2048,
    val temperature: Double = 0.8,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

private val Context.settingsStore by preferencesDataStore(name = "soullink_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val MAX_TOKENS = stringPreferencesKey("max_tokens")
        val TEMPERATURE = stringPreferencesKey("temperature")
    }

    val flow: Flow<ApiSettings> = context.settingsStore.data.map { prefs ->
        ApiSettings(
            baseUrl = prefs[Keys.BASE_URL].orEmpty(),
            apiKey = prefs[Keys.API_KEY].orEmpty(),
            model = prefs[Keys.MODEL] ?: "gpt-4o-mini",
            maxTokens = prefs[Keys.MAX_TOKENS]?.toIntOrNull() ?: 2048,
            temperature = prefs[Keys.TEMPERATURE]?.toDoubleOrNull() ?: 0.8,
        )
    }

    suspend fun save(settings: ApiSettings) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.BASE_URL] = settings.baseUrl.trim()
            prefs[Keys.API_KEY] = settings.apiKey.trim()
            prefs[Keys.MODEL] = settings.model.trim()
            prefs[Keys.MAX_TOKENS] = settings.maxTokens.toString()
            prefs[Keys.TEMPERATURE] = settings.temperature.toString()
        }
    }
}