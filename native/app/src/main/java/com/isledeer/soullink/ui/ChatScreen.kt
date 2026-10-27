package com.isledeer.soullink.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.isledeer.soullink.data.ProviderConfig
import com.isledeer.soullink.ui.chat.AttachBottomSheet
import com.isledeer.soullink.ui.chat.ChatInputBar
import com.isledeer.soullink.ui.chat.ToolsBottomSheet
import com.isledeer.soullink.ui.components.ModelPickerSheet
import com.isledeer.soullink.util.DocumentParser
import com.isledeer.soullink.util.ImageUtils
import androidx.core.content.FileProvider
import java.io.File
import com.isledeer.soullink.util.VoiceInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 聊天主界面（唯一一级页面）。
 * Drawer 管历史会话与模块入口，主区是顶栏 / 消息流 / 输入区。
 * 状态全部来自 ChatViewModel，这里只做「交互 → 回调」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversations: List<Conversation>,
    current: Conversation?,
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    modelName: String,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenProviderSettings: () -> Unit,
    onOpenWorldBook: () -> Unit,
    onOpenAssistants: () -> Unit,
    onSend: (String, String?) -> Unit,
    onStop: () -> Unit,
    tokenUsage: Int,
    autoCompress: Boolean,
    onToggleAutoCompress: () -> Unit,
    onContinueGeneration: (String) -> Unit,
    onClearContext: () -> Unit,
    thinkingLevel: String,
    onThinkingLevelChange: (String) -> Unit,
    toolStates: Map<String, Boolean>,
    onToggleTool: (String) -> Unit,
    providers: List<ProviderConfig>,
    onPickChatModel: (String) -> Unit,
    searchMaster: Boolean,
    searchEngines: Map<String, Boolean>,
    searchKeys: Map<String, String>,
    isSearchEnabled: Boolean,
    onSearchMasterChange: (Boolean) -> Unit,
    onSearchEngineChange: (String, Boolean) -> Unit,
    onRegenerate: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onBranch: (String) -> Unit,
    onUpdateConversation: (String?, Int?, Double?) -> Unit,
    quickPrompts: List<String>,
    memorySummary: String?,
    mcpServers: List<Pair<String, String>>,
    enabledMcp: Set<String>,
    onToggleMcp: (String) -> Unit,
    onCompact: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var attachVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var actionsTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var toolsVisible by remember { mutableStateOf(false) }
    var modelPickerVisible by remember { mutableStateOf(false) }
    var searchSheetVisible by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var searchDialogVisible by remember { mutableStateOf(false) }
    var usageDialogVisible by remember { mutableStateOf(false) }
    var memoryDialogVisible by remember { mutableStateOf(false) }
    var mcpDialogVisible by remember { mutableStateOf(false) }
    var treeDialogVisible by remember { mutableStateOf(false) }
    var rawDialogVisible by remember { mutableStateOf(false) }
    var contextDialogVisible by remember { mutableStateOf(false) }

    val voice = remember { VoiceInput(context) }
    DisposableEffect(Unit) { onDispose { voice.release() } }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val base64 = withContext(Dispatchers.IO) {
                    ImageUtils.uriToBase64(context, uri)
                }
                if (base64 != null) pendingImage = base64 else notice = "图片读取失败"
            }
        }
    }

    val cameraUri = remember { mutableStateOf<android.net.Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = cameraUri.value
        cameraUri.value = null
        if (ok && uri != null) {
            scope.launch {
                val base64 = withContext(Dispatchers.IO) { ImageUtils.uriToBase64(context, uri) }
                if (base64 != null) pendingImage = base64 else notice = "拍照读取失败"
            }
        }
    }

    val pickDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) { DocumentParser.extract(context, uri) }
                if (text.startsWith("（")) notice = text else input = input + text
            }
        }
    }

    val requestRecord = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voice.listen(
                onPartial = { input = it },
                onResult = { input = it },
                onError = { notice = it },
            )
        } else {
            notice = "需要录音权限才能语音输入"
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty() && listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    notice?.let { text ->
        LaunchedEffect(text) {
            kotlinx.coroutines.delay(2200)
            notice = null
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = GlassTokens.BackgroundBottom,
                modifier = Modifier.fillMaxWidth(0.82f),
            ) {
                DrawerContent(
                    conversations = conversations,
                    currentId = current?.id,
                    onSelect = { id ->
                        onSelectConversation(id)
                        scope.launch { drawerState.close() }
                    },
                    onNewConversation = {
                        onNewConversation()
                        scope.launch { drawerState.close() }
                    },
                    onDeleteConversation = onDeleteConversation,
                    onOpenSettings = {
                        scope.launch { drawerState.close() }
                        onOpenProviderSettings()
                    },
                    onOpenWorldBook = {
                        scope.launch { drawerState.close() }
                        onOpenWorldBook()
                    },
                    onOpenAssistants = {
                        scope.launch { drawerState.close() }
                        onOpenAssistants()
                    },
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(GlassTokens.BackgroundTop, GlassTokens.BackgroundBottom)
                    )
                )
                .statusBarsPadding()
                .imePadding()
        ) {
                ChatTopBar(
                    title = current?.title ?: "SoulLink",
                    modelName = modelName,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    tokenUsage = tokenUsage,
                    autoCompress = autoCompress,
                    onNewChat = onNewConversation,
                    onClearContext = onClearContext,
                    onOpenSettings = { settingsVisible = true },
                    onToggleAutoCompress = onToggleAutoCompress,
                    onSearch = { searchDialogVisible = true },
                    onShareExport = { exportConversation(context, current, messages) },
                    onUsage = { usageDialogVisible = true },
                    onMemory = { memoryDialogVisible = true },
                    onSkills = { onOpenAssistants() },
                    onMcp = { mcpDialogVisible = true },
                    onTree = { treeDialogVisible = true },
                    onRawDebug = { rawDialogVisible = true },
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            onCopy = {
                                clipboard.setText(AnnotatedString(message.text))
                                notice = "已复制"
                            },
                            onRegenerate = { onRegenerate(message.id) },
                            onSpeak = { onSpeak(message.text) },
                            onMore = { actionsTarget = message },
                            onContinue = { onContinueGeneration(message.id) },
                        )
                    }
                }

                notice?.let { text ->
                    Text(
                        text = text,
                        color = GlassTokens.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }

                if (pendingImage != null) {
                    PendingImagePreview(
                        base64 = pendingImage!!,
                        onClear = { pendingImage = null },
                    )
                }

                if (quickPrompts.isNotEmpty() && !isGenerating) {
                    QuickPromptChips(
                        prompts = quickPrompts,
                        onPick = { text -> input = text },
                    )
                }

                ChatInputBar(
                    value = input,
                    onValueChange = { input = it },
                    isGenerating = isGenerating,
                    modelName = modelName,
                    thinkingLevel = thinkingLevel,
                    onThinkingLevelChange = onThinkingLevelChange,
                    isWebSearchEnabled = isSearchEnabled,
                    onSearchClick = { searchSheetVisible = true },
                    onModelClick = { modelPickerVisible = true },
                    onToolsClick = { toolsVisible = true },
                    onAttachClick = { attachVisible = true },
                    onVoiceInput = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            voice.listen(
                                onPartial = { input = it },
                                onResult = { input = it },
                                onError = { notice = it },
                            )
                        } else {
                            requestRecord.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onSlashCommand = { command ->
                        when (command) {
                            "/clear" -> onClearContext()
                            "/compact" -> {
                                onCompact()
                                notice = "已压缩上下文，记忆通过 /memory 查看"
                            }
                            "/memory" -> memoryDialogVisible = true
                            "/thinking" -> notice = "请在下方调节"
                        }
                    },
                    onSend = {
                        if (input.isNotBlank() || pendingImage != null) {
                            onSend(input.trim(), pendingImage)
                            input = ""
                            pendingImage = null
                        }
                    },
                    onStop = onStop,
                )
        }
    }
    if (attachVisible) {
        AttachBottomSheet(
            onDismiss = { attachVisible = false },
            onPickPhoto = {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onFillInput = { text -> input = input + text },
            onCamera = {
                attachVisible = false
                val dir = File(context.cacheDir, "images")
                dir.mkdirs()
                val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraUri.value = uri
                takePicture.launch(uri)
            },
            onWorldBook = {
                attachVisible = false
                onOpenWorldBook()
            },
            onPrompt = {
                attachVisible = false
                settingsVisible = true
            },
            onContext = {
                attachVisible = false
                contextDialogVisible = true
            },
            onSkills = {
                attachVisible = false
                onOpenAssistants()
            },
        )
    }

    actionsTarget?.let { target ->
        MessageActionsSheet(
            message = target,
            onDismiss = { actionsTarget = null },
            onCopy = { clipboard.setText(AnnotatedString(target.text)) },
            onEdit = { editTarget = target },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, target.text)
                }
                context.startActivity(Intent.createChooser(intent, "分享"))
            },
            onBranch = { onBranch(target.id) },
            onFavorite = { onToggleFavorite(target.id) },
            onDelete = { onDeleteMessage(target.id) },
        )
    }

    if (settingsVisible) {
        ConversationSettingsDialog(
            conversation = current,
            onDismiss = { settingsVisible = false },
            onSave = { prompt, tokens, temp -> onUpdateConversation(prompt, tokens, temp) },
        )
    }

    editTarget?.let { target ->
        EditMessageDialog(
            initial = target.text,
            onDismiss = { editTarget = null },
            onConfirm = { text ->
                onEditMessage(target.id, text)
                editTarget = null
            },
        )
    }

    if (searchSheetVisible) {
        com.isledeer.soullink.ui.chat.SearchEngineBottomSheet(
            masterEnabled = searchMaster,
            engineStates = searchEngines,
            engineKeys = searchKeys,
            onMasterChange = onSearchMasterChange,
            onEngineChange = onSearchEngineChange,
            onDismiss = { searchSheetVisible = false },
        )
    }

    if (toolsVisible) {
        ToolsBottomSheet(
            toolStates = toolStates,
            onToggleTool = onToggleTool,
            mcpServers = mcpServers,
            enabledMcp = enabledMcp,
            onToggleMcp = onToggleMcp,
            onDismiss = { toolsVisible = false },
        )
    }

    if (modelPickerVisible) {
        ModelPickerSheet(
            providers = providers,
            onPick = { _, model ->
                onPickChatModel(model)
                modelPickerVisible = false
            },
            onDismiss = { modelPickerVisible = false },
        )
    }

    if (searchDialogVisible) {
        SearchDialog(messages = messages, onDismiss = { searchDialogVisible = false })
    }
    if (usageDialogVisible) {
        UsageDialog(tokenUsage = tokenUsage, messages = messages, onDismiss = { usageDialogVisible = false })
    }
    if (memoryDialogVisible) {
        MemoryDialog(memorySummary = memorySummary, onDismiss = { memoryDialogVisible = false })
    }
    if (mcpDialogVisible) {
        McpDialog(
            mcpServers = mcpServers,
            enabledMcp = enabledMcp,
            onToggleMcp = onToggleMcp,
            onDismiss = { mcpDialogVisible = false },
        )
    }
    if (treeDialogVisible) {
        TreeDialog(messages = messages, onDismiss = { treeDialogVisible = false })
    }
    if (rawDialogVisible) {
        RawLogDialog(onDismiss = { rawDialogVisible = false })
    }
    if (contextDialogVisible) {
        ContextDialog(
            messages = messages,
            tokenUsage = tokenUsage,
            onClear = onClearContext,
            onDismiss = { contextDialogVisible = false },
        )
    }
}

@Composable
private fun PendingImagePreview(base64: String, onClear: () -> Unit) {
    val bitmap = remember(base64) {
        runCatching {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "待发送图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "移除图片",
                color = GlassTokens.Accent,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClear)
                    .padding(6.dp),
            )
        }
    }
}

@Composable
private fun EditMessageDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassTokens.BackgroundBottom.copy(alpha = 0.96f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text("编辑消息", color = GlassTokens.TextPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 3,
                maxLines = 8,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0x14FFFFFF),
                    unfocusedContainerColor = Color(0x0DFFFFFF),
                    focusedTextColor = GlassTokens.TextPrimary,
                    unfocusedTextColor = GlassTokens.TextPrimary,
                    cursorColor = GlassTokens.Primary,
                    focusedIndicatorColor = GlassTokens.Primary.copy(alpha = 0.6f),
                    unfocusedIndicatorColor = Color(0x1FFFFFFF),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消", color = GlassTokens.TextSecondary) }
                TextButton(onClick = { onConfirm(text) }) { Text("保存", color = GlassTokens.Primary) }
            }
        }
    }
}

/* ---------------------------- 快捷短语 ---------------------------- */

@Composable
private fun QuickPromptChips(prompts: List<String>, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        prompts.take(8).forEach { prompt ->
            Text(
                text = prompt,
                color = GlassTokens.TextPrimary,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassTokens.Primary.copy(alpha = 0.14f))
                    .clickable { onPick(prompt) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/* ----------------------------- 导出对话 ----------------------------- */

private fun exportConversation(
    context: android.content.Context,
    conversation: Conversation?,
    messages: List<ChatMessage>,
) {
    val title = conversation?.title ?: "SoulLink 对话"
    val builder = StringBuilder()
    builder.appendLine("# $title")
    builder.appendLine()
    messages.forEach { message ->
        val who = if (message.role == Role.User) "**用户**" else "**助手**"
        builder.appendLine(who)
        builder.appendLine(message.text)
        builder.appendLine()
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, builder.toString())
    }
    context.startActivity(Intent.createChooser(intent, "导出对话"))
}

/* --------------------------- 会话内搜索 --------------------------- */

@Composable
private fun SearchDialog(
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val matched = if (query.isBlank()) emptyList() else messages.filter {
        it.text.contains(query, ignoreCase = true)
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassTokens.BackgroundBottom.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text("在会话中搜索", color = GlassTokens.TextPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text("输入关键词", color = GlassTokens.TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0x14FFFFFF),
                    unfocusedContainerColor = Color(0x0DFFFFFF),
                    focusedTextColor = GlassTokens.TextPrimary,
                    unfocusedTextColor = GlassTokens.TextPrimary,
                    cursorColor = GlassTokens.Primary,
                    focusedIndicatorColor = GlassTokens.Primary.copy(alpha = 0.6f),
                    unfocusedIndicatorColor = Color(0x1FFFFFFF),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            if (matched.isEmpty() && query.isNotBlank()) {
                Text("没有匹配的消息", color = GlassTokens.TextSecondary, fontSize = 12.sp)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                matched.take(20).forEach { message ->
                    val roleLabel = if (message.role == Role.User) "你" else "AI"
                    Text(
                        text = "$roleLabel · ${message.text.take(120)}${if (message.text.length > 120) "…" else ""}",
                        color = GlassTokens.TextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { clipboard.setText(AnnotatedString(message.text)) }
                            .padding(vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("关闭", color = GlassTokens.TextSecondary) }
            }
        }
    }
}

/* --------------------------- Token 用量 --------------------------- */

@Composable
private fun UsageDialog(
    tokenUsage: Int,
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassTokens.BackgroundBottom.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text("Token 用量", color = GlassTokens.TextPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text("累计（按回复统计）· $tokenUsage tok", color = GlassTokens.TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                messages.filter { it.role == Role.Assistant && it.tokenCount > 0 }.forEach { message ->
                    Text(
                        text = "${message.text.take(40)}… · ${message.tokenCount} tok",
                        color = GlassTokens.TextPrimary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("关闭", color = GlassTokens.TextSecondary) }
            }
        }
    }
}

/* --------------------------- 记忆面板 --------------------------- */

@Composable
private fun MemoryDialog(
    memorySummary: String?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassTokens.BackgroundBottom.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text("会话记忆", color = GlassTokens.TextPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = memorySummary ?: "还没有记忆。用输入框 /compact 压缩当前上下文生成记忆；开启「自动压缩」或助手「长期记忆」后长对话会自动总结。",
                color = if (memorySummary == null) GlassTokens.TextSecondary else GlassTokens.TextPrimary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("关闭", color = GlassTokens.TextSecondary) }
            }
        }
    }
}

/* --------------------------- MCP 面板 --------------------------- */

@Composable
private fun McpDialog(
    mcpServers: List<Pair<String, String>>,
    enabledMcp: Set<String>,
    onToggleMcp: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassTokens.BackgroundBottom.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text("会话中的 MCP", color = GlassTokens.TextPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            if (mcpServers.isEmpty()) {
                Text(
                    text = "还没有 MCP 服务器。去 设置 → MCP 添加（HTTP 端点）。",
                    color = GlassTokens.TextSecondary,
                    fontSize = 12.sp,
                )
            } else {
                mcpServers.forEach { (id, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleMcp(id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, color = GlassTokens.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = id in enabledMcp, onCheckedChange = { onToggleMcp(id) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("关闭", color = GlassTokens.TextSecondary) }
            }
        }
    }
}

/* --------------------------- 消息树 --------------------------- */

@Composable
private fun TreeDialog(
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassTokens.BackgroundBottom.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text("消息树", color = GlassTokens.TextPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "重新生成会追加变体；分支从某条消息开新会话。当前路径共 ${messages.size} 轮。",
                color = GlassTokens.TextSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                messages.forEach { message ->
                    val roleLabel = if (message.role == Role.User) "你" else "AI"
                    val variant = if (message.variantCount > 1) " [${message.variantIndex + 1}/${message.variantCount}]" else ""
                    Text(
                        text = "$roleLabel$variant · ${message.text.take(60)}${if (message.text.length > 60) "…" else ""}",
                        color = GlassTokens.TextPrimary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("关闭", color = GlassTokens.TextSecondary) }
            }
        }
    }
}

/* ------------------------- RAW 调试面板 ------------------------- */

@Composable
private fun RawLogDialog(onDismiss: () -> Unit) {
    val entries = com.isledeer.soullink.net.RequestLog.snapshot()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassTokens.BackgroundBottom.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text("RAW 调试面板", color = GlassTokens.TextPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text("最近请求（最新在前，最多 200 条）", color = GlassTokens.TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (entries.isEmpty()) {
                    Text("还没有请求记录", color = GlassTokens.TextSecondary, fontSize = 12.sp)
                } else {
                    entries.forEach { line ->
                        Text(
                            text = line,
                            color = GlassTokens.TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("关闭", color = GlassTokens.TextSecondary) }
            }
        }
    }
}

/* --------------------------- 上下文管理 --------------------------- */

@Composable
private fun ContextDialog(
    messages: List<ChatMessage>,
    tokenUsage: Int,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassTokens.BackgroundBottom.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text("上下文管理", color = GlassTokens.TextPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            Text("当前上下文：${messages.size} 条消息 · 累计 $tokenUsage tok", color = GlassTokens.TextPrimary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "• 清空对话会删除全部消息，可用 /compact 先压成记忆\n• 长对话建议开启「自动压缩」，超过阈值自动摘要",
                color = GlassTokens.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消", color = GlassTokens.TextSecondary) }
                TextButton(
                    onClick = {
                        onClear()
                        onDismiss()
                    },
                ) { Text("清空对话", color = GlassTokens.Accent) }
            }
        }
    }
}
