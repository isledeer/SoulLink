package com.isledeer.soullink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * 聊天页的「接线层」。
 *
 * 现在用本地 state + 假回复跑通交互；接上数据层时，这里换成
 * ViewModel + StateFlow，ChatScreen 本身一行都不用改
 * （它只吃 List<ChatMessage> 和几个回调）。
 *
 * 演示逻辑故意保留：发送后 1.2s 追加一条 assistant 消息，
 * 用来验证「生成中」状态、气泡追加、自动滚底这三件事。
 */

private val DEMO_MODELS = listOf(
    "deepseek-v4.1-flash",
    "gpt-4o",
    "claude-sonnet-4-5",
)

private val DEMO_MESSAGES = listOf(
    ChatMessage(
        id = "demo-1",
        role = Role.Assistant,
        text = "你好，我是 isledeer。Compose 骨架已经跑起来了 —— 顶栏、气泡、输入框都是原生绘制。",
    ),
    ChatMessage(
        id = "demo-2",
        role = Role.User,
        text = "发一条试试",
    ),
    ChatMessage(
        id = "demo-3",
        role = Role.Assistant,
        text = "试试输入框，1.2 秒后会有一条假回复。右上角 ☰ 可以进世界书面板（现在是占位）。",
    ),
)

@Composable
fun ChatRoute(onOpenWorldBook: () -> Unit) {
    var messages by remember { mutableStateOf(DEMO_MESSAGES) }
    var modelName by remember { mutableStateOf(DEMO_MODELS.first()) }
    var isGenerating by remember { mutableStateOf(false) }
    var seq by remember { mutableStateOf(0) }

    LaunchedEffect(isGenerating, seq) {
        if (!isGenerating) return@LaunchedEffect
        delay(1200)
        messages = messages + ChatMessage(
            id = "demo-reply-$seq",
            role = Role.Assistant,
            text = "（演示回复 #$seq）骨架已跑通。接上 OkHttp 流式之后，这里会逐字吐出来。",
        )
        isGenerating = false
    }

    ChatScreen(
        title = "isledeer",
        subtitle = "12 · 陌生",
        modelName = modelName,
        availableModels = DEMO_MODELS,
        messages = messages,
        isGenerating = isGenerating,
        onBack = { /* 聊天是起始页，暂时无处可回 */ },
        onPickModel = { modelName = it },
        onSend = { text ->
            seq += 1
            messages = messages + ChatMessage(
                id = "demo-user-$seq",
                role = Role.User,
                text = text,
            )
            isGenerating = true
        },
        onStop = { isGenerating = false },
        onOpenWorldBook = onOpenWorldBook,
    )
}