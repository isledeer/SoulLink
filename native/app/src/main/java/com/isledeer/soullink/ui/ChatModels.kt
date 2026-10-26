package com.isledeer.soullink.ui

/**
 * UI 层的消息模型（与 Room 实体解耦：实体是存储格式，这个是渲染格式）。
 *
 * reasoning 单独存：思维链不进正文渲染，UI 用独立区域展示；
 * isStreaming 驱动「生成中」光标，isError 让失败文案不进下一次请求上下文。
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
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)