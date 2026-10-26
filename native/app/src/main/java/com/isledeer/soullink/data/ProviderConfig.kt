package com.isledeer.soullink.data

import org.json.JSONArray
import org.json.JSONObject

/** 功能位：把不同用途绑定到不同 Provider + 模型上 */
enum class Feature(val label: String) {
    CHAT("对话"),
    TRANSLATE("翻译"),
    OCR("识图"),
}

data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
)

data class FeatureBinding(
    val providerId: String,
    val model: String,
)

/**
 * 全局设置：Provider 列表 + 功能绑定。
 *
 * 刻意存在 DataStore 里（单条 JSON），而不是 Room：
 * 这是配置不是业务数据，量小、整块读写，改起来不用动表结构。
 */
data class AppSettings(
    val providers: List<ProviderConfig> = emptyList(),
    val bindings: Map<Feature, FeatureBinding> = emptyMap(),
) {
    fun providerFor(feature: Feature): ProviderConfig? {
        val binding = bindings[feature]
            ?: bindings[Feature.CHAT]
            ?: return providers.firstOrNull()
        return providers.firstOrNull { it.id == binding.providerId } ?: providers.firstOrNull()
    }

    fun modelFor(feature: Feature): String =
        bindings[feature]?.model?.takeIf { it.isNotBlank() }
            ?: bindings[Feature.CHAT]?.model?.takeIf { it.isNotBlank() }
            ?: "gpt-4o-mini"

    val isConfigured: Boolean
        get() = providers.any { it.baseUrl.isNotBlank() && it.apiKey.isNotBlank() }

    fun toJson(): String {
        val providerArray = JSONArray()
        providers.forEach { p ->
            providerArray.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("baseUrl", p.baseUrl)
                    .put("apiKey", p.apiKey)
            )
        }

        val bindingObject = JSONObject()
        bindings.forEach { (feature, binding) ->
            bindingObject.put(
                feature.name,
                JSONObject()
                    .put("providerId", binding.providerId)
                    .put("model", binding.model)
            )
        }

        return JSONObject()
            .put("providers", providerArray)
            .put("bindings", bindingObject)
            .toString()
    }

    companion object {
        fun fromJson(raw: String?): AppSettings {
            if (raw.isNullOrBlank()) return AppSettings()
            return runCatching {
                val root = JSONObject(raw)

                val providers = mutableListOf<ProviderConfig>()
                val array = root.optJSONArray("providers")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        providers.add(
                            ProviderConfig(
                                id = obj.optString("id"),
                                name = obj.optString("name"),
                                baseUrl = obj.optString("baseUrl"),
                                apiKey = obj.optString("apiKey"),
                            )
                        )
                    }
                }

                val bindings = mutableMapOf<Feature, FeatureBinding>()
                val bindingObj = root.optJSONObject("bindings")
                if (bindingObj != null) {
                    Feature.entries.forEach { feature ->
                        val obj = bindingObj.optJSONObject(feature.name) ?: return@forEach
                        bindings[feature] = FeatureBinding(
                            providerId = obj.optString("providerId"),
                            model = obj.optString("model"),
                        )
                    }
                }

                AppSettings(providers = providers, bindings = bindings)
            }.getOrElse { AppSettings() }
        }
    }
}