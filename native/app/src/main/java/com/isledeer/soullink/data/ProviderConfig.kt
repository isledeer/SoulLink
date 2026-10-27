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
    /** 余额查询端点（留空则不支持）；path 为点分 JSON 路径 */
    val balanceUrl: String = "",
    val balanceJsonPath: String = "",
    /** 用户勾选启用的模型（JSON 数组字符串，访问用 modelList()） */
    val enabledModelsJson: String = "[]",
    /** Provider 级启用开关 */
    val enabled: Boolean = true,
    /** API 路径（设置页展示用；请求实际走 /v1/chat/completions 与 /v1/models） */
    val apiPath: String = "/chat/completions",
    /** 自定义请求头 / 请求体（JSON 对象字符串，高级设置 Tab 编辑） */
    val customHeaders: String = "{}",
    val customBodies: String = "{}",
    /** 单模型更多设置：{"模型名": {alias/temperature/context/thinking/vision}} */
    val modelSettingsJson: String = "{}",
) {
    fun modelList(): List<String> = AssistantEntity.parseStringArray(enabledModelsJson)
}

data class FeatureBinding(
    val providerId: String,
    val model: String,
)

/**
 * 全局设置。`default*` 是整条配置链的最后一层兜底：
 *   全局默认 < 助手配置 < 对话覆盖
 */
data class AppSettings(
    val providers: List<ProviderConfig> = emptyList(),
    val bindings: Map<Feature, FeatureBinding> = emptyMap(),
    val defaultModel: String = "gpt-4o-mini",
    val defaultTemperature: Double = 0.8,
    val defaultTopP: Double = 1.0,
    val defaultMaxTokens: Int = 2048,
    /** 联网搜索引擎的 API Key（bing / bocha / tavily / exa） */
    val searchKeys: Map<String, String> = emptyMap(),
    /** 颜色模式：system / dark / light（设置页持久化，启动时应用） */
    val colorMode: String = "dark",
    /** 语音合成语速 / 音调 */
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    /** MCP 服务器列表（JSON 数组字符串） */
    val mcpServersJson: String = "[]",
) {
    fun providerFor(feature: Feature): ProviderConfig? {
        val binding = bindings[feature] ?: bindings[Feature.CHAT] ?: return providers.firstOrNull()
        return providers.firstOrNull { it.id == binding.providerId } ?: providers.firstOrNull()
    }

    fun modelFor(feature: Feature): String =
        bindings[feature]?.model?.takeIf { it.isNotBlank() }
            ?: bindings[Feature.CHAT]?.model?.takeIf { it.isNotBlank() }
            ?: defaultModel

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
                JSONObject().put("providerId", binding.providerId).put("model", binding.model)
            )
        }

        return JSONObject()
            .put("providers", providerArray)
            .put("bindings", bindingObject)
            .put("defaultModel", defaultModel)
            .put("defaultTemperature", defaultTemperature)
            .put("defaultTopP", defaultTopP)
            .put("defaultMaxTokens", defaultMaxTokens)
            .put("searchKeys", JSONObject(searchKeys))
            .put("colorMode", colorMode)
            .put("ttsRate", ttsRate.toDouble())
            .put("ttsPitch", ttsPitch.toDouble())
            .put("mcpServersJson", mcpServersJson)
            .toString()
    }

    companion object {
        fun fromJson(raw: String?): AppSettings {
            if (raw.isNullOrBlank()) return AppSettings()
            return runCatching {
                val root = JSONObject(raw)

                val providers = mutableListOf<ProviderConfig>()
                root.optJSONArray("providers")?.let { array ->
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        providers.add(
                            ProviderConfig(
                                id = obj.optString("id"),
                                name = obj.optString("name"),
                                baseUrl = obj.optString("baseUrl"),
                                apiKey = obj.optString("apiKey"),
                                balanceUrl = obj.optString("balanceUrl"),
                                balanceJsonPath = obj.optString("balanceJsonPath"),
                                enabledModelsJson = obj.optString("enabledModelsJson", "[]"),
                                enabled = obj.optBoolean("enabled", true),
                                apiPath = obj.optString("apiPath", "/chat/completions"),
                                customHeaders = obj.optString("customHeaders", "{}"),
                                customBodies = obj.optString("customBodies", "{}"),
                                modelSettingsJson = obj.optString("modelSettingsJson", "{}"),
                            )
                        )
                    }
                }

                val bindings = mutableMapOf<Feature, FeatureBinding>()
                root.optJSONObject("bindings")?.let { bindingObj ->
                    Feature.entries.forEach { feature ->
                        val obj = bindingObj.optJSONObject(feature.name) ?: return@forEach
                        bindings[feature] = FeatureBinding(
                            providerId = obj.optString("providerId"),
                            model = obj.optString("model"),
                        )
                    }
                }

                AppSettings(
                    providers = providers,
                    bindings = bindings,
                    defaultModel = root.optString("defaultModel", "gpt-4o-mini"),
                    defaultTemperature = root.optDouble("defaultTemperature", 0.8),
                    defaultTopP = root.optDouble("defaultTopP", 1.0),
                    defaultMaxTokens = root.optInt("defaultMaxTokens", 2048),
                    searchKeys = root.optJSONObject("searchKeys")?.let { obj ->
                        obj.keys().asSequence().associateWith { key -> obj.optString(key) }
                    } ?: emptyMap(),
                    colorMode = root.optString("colorMode", "dark"),
                    ttsRate = root.optDouble("ttsRate", 1.0).toFloat(),
                    ttsPitch = root.optDouble("ttsPitch", 1.0).toFloat(),
                    mcpServersJson = root.optString("mcpServersJson", "[]"),
                )
            }.getOrElse { AppSettings() }
        }
    }
}