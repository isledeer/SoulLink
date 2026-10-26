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
    onOpenAssistants: () -> Unit,
) {
    val viewModel: ChatViewModel = koinViewModel()

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val tokenUsage by viewModel.tokenUsage.collectAsStateWithLifecycle()
    val autoCompress by viewModel.autoCompress.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentAssistant by viewModel.currentAssistant.collectAsStateWithLifecycle()

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
        onOpenAssistants = onOpenAssistants,
        onSend = { text, image -> viewModel.send(text, image) },
        onStop = viewModel::stop,
        tokenUsage = tokenUsage,
        autoCompress = autoCompress,
        onToggleAutoCompress = viewModel::toggleAutoCompress,
        onContinueGeneration = viewModel::continueGeneration,
        onClearContext = viewModel::clearContext,
        toolStates = mapOf(
            "time" to (currentAssistant?.toolList()?.contains("time") == true),
            "clipboard" to (currentAssistant?.toolList()?.contains("clipboard") == true),
            "tts" to (currentAssistant?.toolList()?.contains("tts") == true),
        ),
        onToggleTool = viewModel::toggleLocalTool,
        providers = settings.providers,
        fetchModels = viewModel::fetchModelsForChat,
        onPickChatModel = viewModel::setAssistantModel,
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