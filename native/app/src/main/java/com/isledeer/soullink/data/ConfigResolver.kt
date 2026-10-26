package com.isledeer.soullink.data

import android.os.Build
import com.isledeer.soullink.tools.LocalTool
import com.isledeer.soullink.tools.LocalTools
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 一次请求的最终配置（三层合并后的结果）。
 * 优先级：全局默认（DataStore） < 助手（AssistantEntity） < 对话（ConversationEntity）
 */
data class ResolvedConfig(
    val provider: ProviderConfig?,
    val model: String,
    val temperature: Double,
    val topP: Double,
    val maxTokens: Int,
    val systemPrompt: String,
    val contextLimit: Int,
    val tools: List<LocalTool>,
    val headers: Map<String, String>,
    val bodyExtras: Map<String, String>,
) {
    val isConfigured: Boolean
        get() = provider != null && provider.baseUrl.isNotBlank() && provider.apiKey.isNotBlank()
}

object ConfigResolver {

    fun resolve(
        settings: AppSettings,
        assistant: AssistantEntity?,
        conversation: ConversationEntity?,
        tools: LocalTools,
    ): ResolvedConfig {
        val provider = settings.providerFor(Feature.CHAT)

        val model = assistant?.chatModelId?.takeIf { it.isNotBlank() }
            ?: settings.modelFor(Feature.CHAT)

        val temperature = when {
            conversation?.hasTemperatureOverride == true -> conversation.temperature
            assistant?.temperature != null -> assistant.temperature.toDouble()
            else -> settings.defaultTemperature
        }

        val maxTokens = when {
            conversation?.hasMaxTokensOverride == true -> conversation.maxTokens
            assistant?.maxTokens != null && assistant.maxTokens > 0 -> assistant.maxTokens
            else -> settings.defaultMaxTokens
        }

        // System Prompt：对话覆盖 > 助手 > 全局空
        val rawPrompt = conversation?.systemPrompt?.takeIf { it.isNotBlank() }
            ?: assistant?.systemPrompt.orEmpty()

        return ResolvedConfig(
            provider = provider,
            model = model,
            temperature = temperature,
            topP = assistant?.topP?.toDouble() ?: settings.defaultTopP,
            maxTokens = maxTokens,
            systemPrompt = render(rawPrompt, model),
            contextLimit = assistant?.contextMessageLimit ?: 0,
            tools = tools.resolve(assistant?.toolList().orEmpty()),
            headers = assistant?.headerMap().orEmpty(),
            bodyExtras = assistant?.bodyMap().orEmpty(),
        )
    }

    /**
     * 变量替换。刻意不用 java.time（minSdk 24 没有，需 desugar），
     * 统一走 SimpleDateFormat。
     */
    fun render(template: String, model: String): String {
        if (template.isBlank()) return ""

        val now = Date()
        val zone = TimeZone.getDefault()

        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = zone }
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply { timeZone = zone }

        return template
            .replace("{{date}}", date.format(now))
            .replace("{{time}}", time.format(now))
            .replace("{{datetime}}", "${date.format(now)} ${time.format(now)}")
            .replace("{{timezone}}", zone.id)
            .replace("{{model}}", model)
            .replace("{{device_info}}", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
    }

    /** 助手消息模板：{{message}} / {{role}} */
    fun applyTemplate(template: String?, message: String, role: String): String {
        if (template.isNullOrBlank()) return message
        return template
            .replace("{{message}}", message)
            .replace("{{role}}", role)
    }
}