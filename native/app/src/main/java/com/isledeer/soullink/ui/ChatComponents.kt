package com.isledeer.soullink.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.isledeer.soullink.ui.chat.TopBarDropdownMenu
import com.isledeer.soullink.ui.markdown.MarkdownMessage

/* ------------------------------ 顶栏 ------------------------------ */

@Composable
fun ChatTopBar(
    title: String,
    modelName: String,
    onOpenDrawer: () -> Unit,
    tokenUsage: Int,
    autoCompress: Boolean,
    onNewChat: () -> Unit,
    onClearContext: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleAutoCompress: () -> Unit,
) {
    var moreExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .liquidGlass(cornerRadius = GlassTokens.BarRadius)
            .heightIn(min = 52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(Icons.Outlined.Menu, contentDescription = "菜单", tint = GlassTokens.TextSecondary, modifier = Modifier.size(22.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.Center,
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
                text = modelName,
                color = GlassTokens.TextSecondary.copy(alpha = 0.6f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier.fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = { moreExpanded = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = GlassTokens.TextSecondary, modifier = Modifier.size(22.dp))
            }
            TopBarDropdownMenu(
                expanded = moreExpanded,
                onDismiss = { moreExpanded = false },
                tokenUsage = tokenUsage,
                autoCompress = autoCompress,
                onNewChat = onNewChat,
                onClearContext = onClearContext,
                onOpenSettings = onOpenSettings,
                onToggleAutoCompress = onToggleAutoCompress,
            )
        }
    }
}

@Composable
fun CircleIconButton(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = GlassTokens.TextSecondary, fontSize = 16.sp)
    }
}

/* ------------------------------ 气泡 ------------------------------ */

@Composable
fun MessageBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onSpeak: () -> Unit,
    onMore: () -> Unit,
    onContinue: () -> Unit,
) {
    val isUser = message.role == Role.User

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = when {
                isUser -> GlassTokens.BubbleUser
                message.isError -> GlassTokens.Accent.copy(alpha = 0.16f)
                else -> GlassTokens.BubbleAi.copy(alpha = 0.78f)
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 310.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                message.imageBase64?.let { base64 ->
                    val bitmap = rememberThumbnail(base64)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .heightIn(max = 180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .padding(bottom = 6.dp),
                        )
                    }
                }

                val reasoning = message.reasoning
                if (!reasoning.isNullOrBlank()) {
                    Text(
                        text = if (message.isStreaming) "思考中…" else "深度思考",
                        color = GlassTokens.TextSecondary.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = reasoning,
                        color = GlassTokens.TextSecondary.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        maxLines = if (message.isStreaming) 6 else 12,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                    )
                }

                val body = when {
                    message.text.isNotBlank() -> message.text
                    message.isStreaming -> "…"
                    else -> ""
                }
                if (body.isNotEmpty()) {
                    if (message.isStreaming || isUser) {
                        Text(
                            text = if (message.isStreaming) "$body ▍" else body,
                            color = when {
                                message.isError -> GlassTokens.Accent
                                isUser -> Color.White
                                else -> GlassTokens.TextPrimary
                            },
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                        )
                    } else {
                        MarkdownMessage(
                            text = body,
                            textColor = if (message.isError) GlassTokens.Accent else GlassTokens.TextPrimary,
                        )
                    }
                }
            }
        }

        if (!message.isStreaming) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionText("复制", onCopy)
                if (!isUser) {
                    ActionText("重生成", onRegenerate)
                    ActionText("朗读", onSpeak)
                    if (message.isInterrupted) {
                        ActionText("继续生成", onContinue)
                    }
                }
                ActionText("更多", onMore)

                if (!isUser && message.generationSpeed > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${formatSpeed(message.generationSpeed)} tok/s · ${message.tokenCount} tok",
                        color = GlassTokens.TextSecondary.copy(alpha = 0.55f),
                        fontSize = 9.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionText(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = GlassTokens.TextSecondary.copy(alpha = 0.8f),
        fontSize = 10.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

private fun formatSpeed(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return rounded.toString()
}

@Composable
private fun rememberThumbnail(base64: String): Bitmap? = remember(base64) {
    runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
