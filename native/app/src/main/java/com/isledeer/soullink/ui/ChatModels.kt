package com.isledeer.soullink.ui

/**
 * UI 层模型。nodeId / variant* 支撑消息树的「多版本切换」。
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
    val nodeId: String,
    val role: Role,
    val text: String,
    val reasoning: String? = null,
    val imageBase64: String? = null,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val isInterrupted: Boolean = false,
    val isFavorite: Boolean = false,
    val generationSpeed: Double = 0.0,
    val tokenCount: Int = 0,
    /** 同一节点下的变体总数与当前下标（>1 时 UI 显示 [< 1/N >]） */
    val variantCount: Int = 1,
    val variantIndex: Int = 0,
    /** 模式 B 的 ABCD 选项（来自消息 parts 的 choices） */
    val choices: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

data class Conversation(
    val id: String,
    val title: String,
    val assistantId: String,
    /** 绑定的游戏存档 id（null = 普通聊天） */
    val sessionId: String? = null,
    val systemPrompt: String = "",
    val maxTokens: Int = 0,
    val temperature: Double = -1.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)