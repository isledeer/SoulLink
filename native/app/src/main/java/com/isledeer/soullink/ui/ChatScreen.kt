package com.isledeer.soullink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 单列沉浸式聊天界面（Telegram / ChatGPT 移动端那一路）。
 *
 * 纯展示组件：消息、生成态、模型名全部由外部通过参数喂进来，
 * 这样接 ViewModel、接假数据、接测试都能复用同一份 UI。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    title: String,
    subtitle: String,
    modelName: String,
    availableModels: List<String>,
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    onBack: () -> Unit,
    onPickModel: (String) -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onOpenWorldBook: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    var input by remember { mutableStateOf("") }
    var sheetVisible by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 收到新消息 / 流式追加时滚到底
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            val target = messages.lastIndex
            if (listState.layoutInfo.totalItemsCount > 0) {
                listState.animateScrollToItem(target)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(GlassTokens.BackgroundTop, GlassTokens.BackgroundBottom)
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatTopBar(
                title = title,
                subtitle = subtitle,
                modelName = modelName,
                onBack = onBack,
                onOpenModels = { sheetVisible = true },
                onOpenWorldBook = onOpenWorldBook,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(msg)
                }
            }

            ChatInputBar(
                value = input,
                onValueChange = { input = it },
                isGenerating = isGenerating,
                onSend = {
                    val trimmed = input.trim()
                    if (trimmed.isNotEmpty()) {
                        onSend(trimmed)
                        input = ""
                    }
                },
                onStop = onStop,
            )
        }
    }

    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xF2070B14),
        ) {
            ModelSheetContent(
                current = modelName,
                models = availableModels,
                onPick = { picked ->
                    onPickModel(picked)
                    sheetVisible = false
                },
                onOpenSettings = {
                    sheetVisible = false
                    onOpenSettings()
                },
            )
        }
    }
}

/* ------------------------------- 顶栏 ------------------------------- */

@Composable
private fun ChatTopBar(
    title: String,
    subtitle: String,
    modelName: String,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenWorldBook: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .liquidGlass(cornerRadius = GlassTokens.BarRadius)
            .heightIn(min = 52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("←", color = GlassTokens.TextSecondary, fontSize = 18.sp)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = title,
                color = GlassTokens.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = GlassTokens.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onOpenModels)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = modelName,
                color = GlassTokens.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 104.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("▾", color = GlassTokens.TextSecondary, fontSize = 9.sp)
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenWorldBook),
            contentAlignment = Alignment.Center,
        ) {
            Text("☰", color = GlassTokens.TextSecondary, fontSize = 15.sp)
        }
    }
}

/* ------------------------------ 气泡 ------------------------------- */

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == Role.User

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = when {
                isUser -> GlassTokens.BubbleUser
                msg.isError -> GlassTokens.Accent.copy(alpha = 0.16f)
                else -> GlassTokens.BubbleAi.copy(alpha = 0.78f)
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                // 思维链：独立区域、弱化呈现，不混进正文
                val reasoning = msg.reasoning
                if (!reasoning.isNullOrBlank()) {
                    Text(
                        text = if (msg.isStreaming) "思考中…" else "深度思考",
                        color = GlassTokens.TextSecondary.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = reasoning,
                        color = GlassTokens.TextSecondary.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        maxLines = if (msg.isStreaming) 6 else 10,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                    )
                }

                val body = when {
                    msg.text.isNotBlank() -> msg.text
                    msg.isStreaming -> "…"
                    else -> ""
                }

                if (body.isNotEmpty()) {
                    Text(
                        text = if (msg.isStreaming) body + " ▍" else body,
                        color = if (msg.isError) GlassTokens.Accent else if (isUser) Color.White else GlassTokens.TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                    )
                }
            }
        }
    }
}

/* ----------------------------- 输入栏 ------------------------------ */

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .liquidGlass(cornerRadius = GlassTokens.PillRadius)
                .heightIn(min = 46.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = GlassTokens.TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                ),
                cursorBrush = SolidColor(GlassTokens.Primary),
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = if (isGenerating) "生成中，可先输入下一条…" else "输入消息…",
                            color = GlassTokens.TextSecondary.copy(alpha = 0.6f),
                            fontSize = 15.sp,
                        )
                    }
                    innerTextField()
                },
            )
        }

        Spacer(Modifier.width(8.dp))

        Surface(
            onClick = { if (isGenerating) onStop() else onSend() },
            shape = CircleShape,
            color = if (isGenerating) GlassTokens.BubbleAi else GlassTokens.Primary,
            modifier = Modifier.size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (isGenerating) "■" else "↑",
                    color = if (isGenerating) GlassTokens.Accent else Color.White,
                    fontSize = if (isGenerating) 14.sp else 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/* --------------------------- 模型选择抽屉 --------------------------- */

@Composable
private fun ModelSheetContent(
    current: String,
    models: List<String>,
    onPick: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "模型",
            color = GlassTokens.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (models.isEmpty()) {
            Text(
                text = "还没有配置模型",
                color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            models.forEach { model ->
                val active = model == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (active) GlassTokens.Primary.copy(alpha = 0.14f) else Color.Transparent
                        )
                        .clickable { onPick(model) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = model,
                        color = if (active) GlassTokens.Primary else GlassTokens.TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (active) {
                        Text("✓", color = GlassTokens.Primary, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "API 设置",
                color = GlassTokens.TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            Text("›", color = GlassTokens.TextSecondary, fontSize = 15.sp)
        }

        Spacer(Modifier.height(24.dp))
    }
}