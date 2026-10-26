package com.isledeer.soullink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/* ----------------------------- 侧边抽屉 ----------------------------- */

private data class DrawerEntry(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)

private val CORE_ENTRIES = listOf(
    DrawerEntry(Icons.Outlined.Person, "角色"),
    DrawerEntry(Icons.Outlined.SmartToy, "助手"),
    DrawerEntry(Icons.Outlined.Hub, "MCP"),
    DrawerEntry(Icons.Outlined.MenuBook, "故事书"),
    DrawerEntry(Icons.Outlined.TravelExplore, "沉入世界"),
)

@Composable
fun DrawerContent(
    conversations: List<Conversation>,
    currentId: String?,
    onSelect: (String) -> Unit,
    onNewConversation: () -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWorldBook: () -> Unit,
    onOpenAssistants: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(GlassTokens.BackgroundBottom)
            .padding(horizontal = 14.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(GlassTokens.Primary, GlassTokens.Accent)
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("S", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("SoulLink", color = GlassTokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("本地 · 无中转", color = GlassTokens.TextSecondary, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(cornerRadius = 16.dp)
                .clickable(onClick = onNewConversation)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.AddComment, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text("新建聊天", color = GlassTokens.TextPrimary, fontSize = 14.sp)
        }

        Spacer(Modifier.height(14.dp))

        CORE_ENTRIES.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        when (entry.label) {
                            "助手" -> onOpenAssistants()
                            "故事书" -> onOpenWorldBook()
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(entry.label, color = GlassTokens.TextPrimary, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "历史会话",
            color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 10.dp, bottom = 6.dp),
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(conversations, key = { it.id }) { conversation ->
                val active = conversation.id == currentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (active) GlassTokens.Primary.copy(alpha = 0.14f) else Color.Transparent
                        )
                        .clickable { onSelect(conversation.id) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = conversation.title,
                        color = if (active) GlassTokens.Primary else GlassTokens.TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "删除会话",
                        tint = GlassTokens.TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { onDeleteConversation(conversation.id) }
                            .padding(2.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text("设置", color = GlassTokens.TextPrimary, fontSize = 14.sp)
        }
    }
}

/* -------------------------- 消息操作抽屉 -------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onBranch: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xF2070B14),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SheetRow("复制", "拷贝正文到剪贴板", onCopy, onDismiss)
            SheetRow("编辑", "修改后重新发送", onEdit, onDismiss)
            SheetRow("分享", "通过系统分享", onShare, onDismiss)
            SheetRow("创建分支", "从这条消息开一个新会话", onBranch, onDismiss)
            SheetRow(
                if (message.isFavorite) "取消收藏" else "收藏",
                "标记这条消息",
                onFavorite,
                onDismiss,
            )
            SheetRow("删除", "不可恢复", onDelete, onDismiss, danger = true)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetRow(
    title: String,
    subtitle: String,
    action: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                action()
                onDismiss()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (danger) GlassTokens.Accent else GlassTokens.TextPrimary,
                fontSize = 14.sp,
            )
            Text(
                text = subtitle,
                color = GlassTokens.TextSecondary.copy(alpha = 0.6f),
                fontSize = 11.sp,
            )
        }
        Text("›", color = GlassTokens.TextSecondary, fontSize = 16.sp)
    }
}

/* ------------------------ 附件 / 快捷菜单 ------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onCamera: () -> Unit,
    onVoice: () -> Unit,
    onDocument: () -> Unit,
    onFile: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xF2070B14),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SheetRow("照片", "从相册选择图片（多模态）", onPickPhoto, onDismiss)
            SheetRow("拍照", "调用系统相机拍摄", onCamera, onDismiss)
            SheetRow("语音输入", "用系统识别把语音转成文字", onVoice, onDismiss)
            SheetRow("文档", "PDF / DOCX / TXT 提取文本", onDocument, onDismiss)
            SheetRow("文件", "暂未开放", onFile, onDismiss)
            SheetRow("网络搜索", "当前版本未接入", {}, onDismiss)
            SheetRow("MCP", "未连接任何 MCP 服务", {}, onDismiss)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ------------------------- 对话级设置卡片 ------------------------- */

@Composable
fun ConversationSettingsDialog(
    conversation: Conversation?,
    onDismiss: () -> Unit,
    onSave: (systemPrompt: String, maxTokens: Int, temperature: Double) -> Unit,
) {
    if (conversation == null) return

    var systemPrompt by remember(conversation.id) { mutableStateOf(conversation.systemPrompt) }
    var maxTokens by remember(conversation.id) { mutableFloatStateOf(conversation.maxTokens.toFloat()) }
    var temperature by remember(conversation.id) { mutableFloatStateOf(conversation.temperature.toFloat()) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(cornerRadius = 24.dp)
                .background(GlassTokens.BackgroundBottom.copy(alpha = 0.92f))
                .padding(20.dp)
        ) {
            Text("对话设置", color = GlassTokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            Text("系统提示（仅本条对话）", color = GlassTokens.TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(14.dp),
                placeholder = {
                    Text("例如：你是…，始终以第一人称回答。", color = GlassTokens.TextSecondary.copy(alpha = 0.5f), fontSize = 13.sp)
                },
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

            Spacer(Modifier.height(14.dp))
            Text("最大输出 ${maxTokens.toInt()} tokens", color = GlassTokens.TextSecondary, fontSize = 11.sp)
            Slider(
                value = maxTokens,
                onValueChange = { maxTokens = it },
                valueRange = 256f..8192f,
            )

            Text("温度 ${((temperature * 100).toInt() / 100.0)}", color = GlassTokens.TextSecondary, fontSize = 11.sp)
            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                valueRange = 0f..2f,
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = GlassTokens.TextSecondary)
                }
                TextButton(
                    onClick = {
                        onSave(systemPrompt, maxTokens.toInt(), temperature.toDouble())
                        onDismiss()
                    }
                ) {
                    Text("保存", color = GlassTokens.Primary)
                }
            }
        }
    }
}

/* ---------------------------- 快捷短语 ---------------------------- */

private val QUICK_PROMPTS = listOf(
    "继续",
    "换个说法",
    "展开细节",
    "简短一点",
    "用第一人称重写",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPromptsSheet(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xF2070B14),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("快捷短语", color = GlassTokens.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            QUICK_PROMPTS.forEach { prompt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onPick(prompt)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Text(prompt, color = GlassTokens.TextPrimary, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}