package com.isledeer.soullink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isledeer.soullink.data.Feature
import org.koin.androidx.compose.koinViewModel

/** 聊天页接线：ViewModel → ChatScreen（零业务逻辑） */
@Composable
fun ChatRoute(
    onOpenProviderSettings: () -> Unit,
    onOpenWorldBook: () -> Unit,
) {
    val viewModel: ChatViewModel = koinViewModel()

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    ChatScreen(
        conversations = conversations,
        current = current,
        messages = messages,
        isGenerating = isGenerating,
        modelName = settings.modelFor(Feature.CHAT),
        onSelectConversation = viewModel::selectConversation,
        onNewConversation = viewModel::newConversation,
        onDeleteConversation = viewModel::deleteConversation,
        onOpenProviderSettings = onOpenProviderSettings,
        onOpenWorldBook = onOpenWorldBook,
        onSend = { text, image -> viewModel.send(text, image) },
        onStop = viewModel::stop,
        onRegenerate = viewModel::regenerate,
        onDeleteMessage = viewModel::deleteMessage,
        onEditMessage = viewModel::editMessage,
        onToggleFavorite = viewModel::toggleFavorite,
        onSpeak = viewModel::speak,
        onBranch = viewModel::branchFrom,
        onUpdateConversation = { prompt, tokens, temp ->
            viewModel.updateCurrentConversation(prompt, tokens, temp)
        },
    )
}