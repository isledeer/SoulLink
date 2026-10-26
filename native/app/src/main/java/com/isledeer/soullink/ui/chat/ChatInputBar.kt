package com.isledeer.soullink.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isledeer.soullink.ui.GlassTokens

private val THINKING_LEVELS = listOf("OFF", "LOW", "MEDIUM", "HIGH", "XHIGH")

/**
 * 聊天输入栏：单卡片双行结构。
 * 第一行 文本 + 麦克风；第二行 模型 / 联网 / 思考深度 / 工具箱 / / / 附件 / 发送。
 */
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isGenerating: Boolean,
    thinkingLevel: String,
    onThinkingLevelChange: (String) -> Unit,
    isWebSearchEnabled: Boolean,
    onToggleWebSearch: () -> Unit,
    onModelClick: () -> Unit,
    onToolsClick: () -> Unit,
    onAttachClick: () -> Unit,
    onVoiceInput: () -> Unit,
    onSlashCommand: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    var thinkingMenu by remember { mutableStateOf(false) }
    var slashMenu by remember { mutableStateOf(false) }
    val canSend = value.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1E1E1E))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        /* 第一行：文本 + 语音 */
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                maxLines = 5,
                textStyle = TextStyle(color = GlassTokens.TextPrimary, fontSize = 15.sp, lineHeight = 21.sp),
                cursorBrush = SolidColor(GlassTokens.Primary),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = if (isGenerating) "生成中…" else "输入消息…",
                            color = GlassTokens.TextSecondary.copy(alpha = 0.55f),
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                },
            )
            IconButton(onClick = onVoiceInput) {
                Icon(
                    Icons.Outlined.Mic,
                    contentDescription = "语音输入",
                    tint = GlassTokens.TextSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        /* 第二行：控制底栏 */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onModelClick) {
                Icon(Icons.Outlined.Memory, contentDescription = "模型", tint = GlassTokens.TextSecondary, modifier = Modifier.size(20.dp))
            }

            IconButton(onClick = onToggleWebSearch) {
                Icon(
                    Icons.Outlined.Public,
                    contentDescription = "联网搜索",
                    tint = if (isWebSearchEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            Box {
                Text(
                    text = thinkingLevel,
                    color = if (thinkingLevel == "OFF") GlassTokens.TextSecondary else MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { thinkingMenu = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                ThinkingPopup(
                    expanded = thinkingMenu,
                    onDismiss = { thinkingMenu = false },
                    current = thinkingLevel,
                    onPick = { level -> onThinkingLevelChange(level); thinkingMenu = false },
                )
            }

            IconButton(onClick = onToolsClick) {
                Icon(Icons.Outlined.Build, contentDescription = "工具箱", tint = GlassTokens.TextSecondary, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.weight(1f))

            Box {
                Text(
                    text = "/",
                    color = GlassTokens.TextSecondary,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { slashMenu = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                SlashCommandPopup(
                    expanded = slashMenu,
                    onDismiss = { slashMenu = false },
                    onCommand = onSlashCommand,
                )
            }

            IconButton(onClick = onAttachClick) {
                Icon(Icons.Outlined.Add, contentDescription = "附件", tint = GlassTokens.TextSecondary, modifier = Modifier.size(22.dp))
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isGenerating -> GlassTokens.Accent
                            canSend -> GlassTokens.Primary
                            else -> Color(0x22FFFFFF)
                        }
                    )
                    .clickable(enabled = canSend || isGenerating) {
                        if (isGenerating) onStop() else onSend()
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isGenerating) {
                    Icon(Icons.Outlined.Stop, contentDescription = "停止", tint = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text("↑", color = if (canSend) Color.White else Color(0x66FFFFFF), fontSize = 18.sp)
                }
            }
        }
    }
}

/** 思考深度弹窗：5 档水平排列，选中项高亮 */
@Composable
fun ThinkingPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    current: String,
    onPick: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE61E1E1E)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            THINKING_LEVELS.forEach { level ->
                val selected = level == current
                Text(
                    text = level,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else Color(0xFFE5E7EB),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onPick(level) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
    }
}