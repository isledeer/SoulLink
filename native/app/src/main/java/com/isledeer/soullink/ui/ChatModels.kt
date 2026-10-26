package com.isledeer.soullink.ui

/**
 * UI 层模型（与 Room 实体解耦）。
 * reasoning 独立字段：思维链不进正文渲染；generationSpeed/tokenCount 供极客统计展示。
 */
enum class Role(val apiName: String) {
    User("user"),
    Assistant("assistant");

    companion object {
        fun fromApi(name: String): Role =
            if (name.equals("assistant", ignoreCase = true)) Assistant else User
    }
}

data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String,
    val reasoning: String? = null,
    val imageBase64: String? = null,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val isFavorite: Boolean = false,
    /** tok/s，仅 assistant 消息有值 */
    val generationSpeed: Double = 0.0,
    /** 该条回复消耗的 token（取 usage，缺失时估算） */
    val tokenCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

data class Conversation(
    val id: String,
    val title: String,
    val systemPrompt: String = "",
    val maxTokens: Int = 2048,
    val temperature: Double = 0.8,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)