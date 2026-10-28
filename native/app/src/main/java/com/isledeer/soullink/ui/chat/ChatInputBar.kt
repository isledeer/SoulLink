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
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
    modelName: String,
    thinkingLevel: String,
    onThinkingLevelChange: (String) -> Unit,
    isWebSearchEnabled: Boolean,
    onSearchClick: () -> Unit,
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
            .clip(RoundedCornerShape(GlassTokens.OverlayRadius))
            .background(GlassTokens.InputBg)
            .border(BorderStroke(1.dp, GlassTokens.GlassBorder), RoundedCornerShape(GlassTokens.OverlayRadius))
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
                BrandIcon(modelName = modelName, size = 20.dp)
            }

            IconButton(onClick = onSearchClick) {
                Icon(
                    Icons.Outlined.Public,
                    contentDescription = "联网搜索",
                    tint = if (isWebSearchEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            Box {
                IconButton(onClick = { thinkingMenu = !thinkingMenu }) {
                    Icon(
                        Icons.Outlined.Lightbulb,
                        contentDescription = "思考深度",
                        tint = if (thinkingLevel == "OFF") GlassTokens.TextSecondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                ThinkingSliderPopup(
                    expanded = thinkingMenu,
                    onDismiss = { thinkingMenu = false },
                    current = thinkingLevel,
                    onPick = onThinkingLevelChange,
                )
            }

            IconButton(onClick = onToolsClick) {
                Icon(Icons.Outlined.Build, contentDescription = "工具箱", tint = GlassTokens.TextSecondary, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.weight(1f))

            Box {
                Icon(
                    imageVector = Icons.Outlined.Terminal,
                    contentDescription = "斜杠指令",
                    tint = GlassTokens.TextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { slashMenu = true },
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

            val sendScale by animateFloatAsState(
                targetValue = if (canSend || isGenerating) 1f else 0.94f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "sendScale",
            )
            val stopRotate by animateFloatAsState(
                targetValue = if (isGenerating) 90f else 0f,
                animationSpec = tween(220),
                label = "stopRotate",
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .scale(sendScale)
                    .then(
                        if (isGenerating) Modifier.background(GlassTokens.Accent)
                        else if (canSend) Modifier.background(
                            Brush.linearGradient(listOf(GlassTokens.Primary, GlassTokens.PrimaryDeep))
                        )
                        else Modifier.background(GlassTokens.Divider)
                    )
                    .clickable(enabled = canSend || isGenerating) {
                        if (isGenerating) onStop() else onSend()
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isGenerating) {
                    Icon(Icons.Outlined.Stop, contentDescription = "停止", tint = Color.White, modifier = Modifier.size(20.dp).rotate(stopRotate))
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "发送",
                        tint = if (canSend) Color.White else GlassTokens.TextDisabled,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
