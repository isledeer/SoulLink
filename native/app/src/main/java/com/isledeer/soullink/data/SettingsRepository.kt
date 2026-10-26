package com.isledeer.soullink.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "soullink_settings")

/**
 * 全部配置存成一条 JSON（见 AppSettings.toJson）。
 * 量小、整块读写，省掉一套 Room 表，也方便将来导出/导入配置。
 */
class SettingsRepository(private val context: Context) {

    private val KEY = stringPreferencesKey("app_settings_json")

    val flow: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings.fromJson(prefs[KEY])
    }

    suspend fun save(settings: AppSettings) {
        context.settingsStore.edit { prefs ->
            prefs[KEY] = settings.toJson()
        }
    }
}