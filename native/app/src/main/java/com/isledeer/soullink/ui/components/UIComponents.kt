package com.isledeer.soullink.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isledeer.soullink.ui.GlassTokens

/* ------------------------- 强调排版（模拟 Expressive） ------------------------- */

/**
 * 不升级 material3 1.4-alpha 的前提下，用字重 + 字距模拟 *Emphasized 语义。
 * 全项目标题类文字统一走这里，保证观感一致。
 */
object Emphasized {
    val titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
    )
    val titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
    )
    val titleSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
    )
    val body = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    )
    val caption = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    )
}

@Composable
fun EmphasizedText(
    text: String,
    style: TextStyle = Emphasized.body,
    color: Color = GlassTokens.TextPrimary,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/* -------------------------------- CardGroup -------------------------------- */

/**
 * 分组卡片：外层 20dp 圆角，内层条目 4dp 圆角（Material 3 列表风格的仿写）。
 * 外层用半透明底 + 内边距做出「槽」的观感，条目之间靠 2dp 间距分隔，不用分割线。
 */
@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) {
            EmphasizedText(
                text = title,
                style = Emphasized.caption,
                color = GlassTokens.TextSecondary,
                modifier = Modifier.padding(start = 14.dp, bottom = 6.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(GlassTokens.BubbleAi.copy(alpha = 0.5f))
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/**
 * 分组内的一行。首尾项的圆角可通过参数放大（配合 animateDpAsState 平滑过渡）。
 */
@Composable
fun CardGroupItem(
    modifier: Modifier = Modifier,
    cornerTop: Dp = 4.dp,
    cornerBottom: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val top by animateDpAsState(targetValue = cornerTop, label = "cornerTop")
    val bottom by animateDpAsState(targetValue = cornerBottom, label = "cornerBottom")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = top,
                    topEnd = top,
                    bottomStart = bottom,
                    bottomEnd = bottom,
                )
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(Color(0x0DFFFFFF))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
}

/* --------------------------------- FormItem -------------------------------- */

/**
 * 标准表单行：左侧标题 + 描述，右侧 tail（Switch / 箭头 / 模型名），
 * 下方可选内容区（TextField、Slider 等）。
 */
@Composable
fun FormItem(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    tail: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    CardGroupItem(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                EmphasizedText(text = title, style = Emphasized.titleSmall)
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    EmphasizedText(
                        text = description,
                        style = Emphasized.caption,
                        color = GlassTokens.TextSecondary.copy(alpha = 0.6f),
                    )
                }
            }
            if (tail != null) {
                Spacer(Modifier.width(12.dp))
                tail()
            }
        }
        if (content != null) {
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/* --------------------------------- UIAvatar -------------------------------- */

/**
 * 三态头像：
 *  - Emoji：直接居中渲染（自动缩放交给 fontSize）
 *  - 首字母：随机但稳定的渐变底色
 *  - 图片：暂时只支持 emoji / 字母（接网络图要额外依赖，先不引）
 */
@Composable
fun UIAvatar(
    text: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val seed = (text ?: "?").hashCode()
    val palette = listOf(
        listOf(Color(0xFF0EA5E9), Color(0xFF6366F1)),
        listOf(Color(0xFFF43F5E), Color(0xFFF59E0B)),
        listOf(Color(0xFF10B981), Color(0xFF0EA5E9)),
        listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)),
    )
    val colors = palette[(seed and 0x7FFFFFFF) % palette.size]

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        val label = text?.trim().orEmpty().ifBlank { "?" }
        val isEmoji = label.length <= 2
        Text(
            text = if (isEmoji) label else label.take(1).uppercase(),
            color = Color.White,
            fontSize = if (isEmoji) (size.value * 0.42f).sp else (size.value * 0.38f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}