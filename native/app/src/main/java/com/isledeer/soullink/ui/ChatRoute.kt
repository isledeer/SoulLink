package com.isledeer.soullink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isledeer.soullink.data.Feature
import com.isledeer.soullink.data.parseMcpServers
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
    val searchMaster by viewModel.searchMaster.collectAsStateWithLifecycle()
    val searchEngines by viewModel.searchEngines.collectAsStateWithLifecycle()
    val isSearchEnabled by viewModel.isSearchEnabled.collectAsStateWithLifecycle()
    val thinkingLevel by viewModel.thinkingLevel.collectAsStateWithLifecycle()
    val currentModelName by viewModel.currentModelName.collectAsStateWithLifecycle()
    val memorySummary by viewModel.memorySummary.collectAsStateWithLifecycle()

    ChatScreen(
        conversations = conversations,
        current = current,
        messages = messages,
        isGenerating = isGenerating,
        modelName = currentModelName ?: "未选择模型",
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
        thinkingLevel = thinkingLevel,
        onThinkingLevelChange = viewModel::setThinkingLevel,
        toolStates = mapOf(
            "time" to (currentAssistant?.toolList()?.contains("time") == true),
            "clipboard" to (currentAssistant?.toolList()?.contains("clipboard") == true),
            "tts" to (currentAssistant?.toolList()?.contains("tts") == true),
        ),
        onToggleTool = viewModel::toggleLocalTool,
        providers = settings.providers,
        onPickChatModel = viewModel::setAssistantModel,
        searchMaster = searchMaster,
        searchEngines = searchEngines,
        searchKeys = settings.searchKeys,
        isSearchEnabled = isSearchEnabled,
        onSearchMasterChange = viewModel::setSearchMaster,
        onSearchEngineChange = viewModel::setSearchEngine,
        onRegenerate = viewModel::regenerate,
        onDeleteMessage = viewModel::deleteMessage,
        onEditMessage = viewModel::editMessage,
        onToggleFavorite = viewModel::toggleFavorite,
        onSpeak = viewModel::speak,
        onBranch = viewModel::branchFrom,
        onUpdateConversation = { prompt, tokens, temp ->
            viewModel.updateCurrentConversation(prompt, tokens, temp)
        },
        quickPrompts = currentAssistant?.promptList().orEmpty(),
        memorySummary = memorySummary,
        mcpServers = parseMcpServers(settings.mcpServersJson).map { it.id to it.name },
        enabledMcp = currentAssistant?.mcpList()?.toSet().orEmpty(),
        onToggleMcp = viewModel::toggleMcpServer,
        onCompact = { viewModel.compactNow() },
    )
}