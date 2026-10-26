package com.isledeer.soullink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * 聊天页接线层：ViewModel → ChatScreen。
 *
 * 这里刻意保持极薄 —— 所有业务都在 ChatViewModel 里，
 * ChatScreen 只吃参数，方便预览、测试和将来换数据源。
 */
@Composable
fun ChatRoute(
    onOpenWorldBook: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: ChatViewModel = koinViewModel()

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val turns = messages.count { it.role == Role.User }

    ChatScreen(
        title = "isledeer",
        subtitle = if (turns == 0) "新的对话" else "$turns 轮 · 本地",
        modelName = settings.model.ifBlank { "未设置模型" },
        availableModels = listOfNotNull(settings.model.takeIf { it.isNotBlank() }),
        messages = messages,
        isGenerating = isGenerating,
        onBack = { /* 聊天是起始页 */ },
        onPickModel = { /* 目前单模型；接 /models 列表后启用 */ },
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onOpenWorldBook = onOpenWorldBook,
        onOpenSettings = onOpenSettings,
    )
}