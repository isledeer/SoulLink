package com.isledeer.soullink.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

/**
 * 玻璃面板 v2。
 *
 * API 31+：haze 真实背景模糊（hazeSource 挂在 AppBackground 上，
 * 面板 hazeEffect 采样其后的极光/噪点/文字内容）。
 * 低版本：haze 自动降级为 tint，再叠一层渐变保持观感。
 * 调用点零改动：签名与 v1 完全一致。
 */
@Composable
fun Modifier.liquidGlass(
    cornerRadius: Dp = GlassTokens.OverlayRadius,
    /** 面板底色（模糊取样后叠加的 tint） */
    tint: Color = GlassTokens.GlassFill,
    /** 顶部高光 */
    highlight: Color = GlassTokens.Highlight,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val hazeState = LocalAppHaze.current

    var result = this.clip(shape)

    result = if (hazeState != null) {
        result.hazeEffect(
            state = hazeState,
            style = HazeStyle(backgroundColor = tint),
        )
    } else {
        // 无 haze 时（理论上不会发生）：降级为渐变玻璃
        result.background(
            Brush.verticalGradient(
                0f to highlight,
                0.42f to GlassTokens.GlassFill,
                1f to GlassTokens.GlassShade,
            )
        )
    }

    // 1dp 高光描边：上亮下暗
    result = result.border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(highlight, highlight.copy(alpha = highlight.alpha * 0.25f)),
        ),
        shape = shape,
    )
    return result
}

/** 全局 HazeState：AppBackground 作为 hazeSource，所有 liquidGlass 面板从这里取 */
val LocalAppHaze = staticCompositionLocalOf<HazeState?> { null }

/**
 * 果冻按压：按压 0.92 + spring 回弹（借鉴 Compose-Symphony 的 bouncyClickable 概念，
 * 只用官方 animation API）。用于关键可点卡片，不扫全量 clickable 防误伤。
 */
@Composable
fun Modifier.bouncyClickable(
    onClick: () -> Unit,
    pressedScale: Float = 0.92f,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "bouncy",
    )
    return this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/**
 * 全局设计令牌（任务卡 4.x 规范唯一真源）。
 * 颜色为可变变量：AppTheme.apply() 切换深浅两套，Activity 重建即生效。
 */
object GlassTokens {

    // 圆角三档：控件 10 / 卡片 16 / 浮层 24（气泡 18/6 特殊保留，不设 token）
    val ControlRadius = 10.dp
    val CardRadius = 16.dp
    val OverlayRadius = 24.dp
    // 兼容旧命名（历史调用点继续用，值已对齐三档）
    val PanelRadius = 24.dp
    val BarRadius = 16.dp
    val PillRadius = 24.dp

    // 面（三阶）
    var BackgroundTop = Color(0xFF0F1526)
    var BackgroundBottom = Color(0xFF0A0E1A)
    var Surface = Color(0xFF12192B)
    var SurfaceElevated = Color(0xFF1B243A)

    // 文字（四阶）
    var TextPrimary = Color(0xFFF1F5F9)
    var TextSecondary = Color(0xB8B6C2D4)
    var TextTertiary = Color(0x8C8C99AD)
    var TextDisabled = Color(0x615F6B80)

    // 品牌与语义
    var Primary = Color(0xFF0EA5E9)
    var PrimaryDeep = Color(0xFF0284C7)
    var Accent = Color(0xFFF43F5E)
    var OnPrimary = Color(0xFFFFFFFF)
    var Success = Color(0xFF22C55E)
    var Warning = Color(0xFFFBBF24)
    var Danger = Color(0xFFF43F5E)

    // 线（三档：分隔 0.06 / 描边 0.10 / 高光 0.16）
    var Divider = Color(0x10FFFFFF)
    var Outline = Color(0x1AFFFFFF)
    var Highlight = Color(0x29FFFFFF)

    // 玻璃 / 输入 / 菜单 / 弹层
    var GlassFill = Color(0x14FFFFFF)
    var GlassShade = Color(0x1A000000)
    var GlassBorder = Color(0x1AFFFFFF)
    var FieldBg = Color(0x0FFFFFFF)
    var FieldBgFocused = Color(0x18FFFFFF)
    var MenuBg = Color(0xF21B243A)
    var SheetBg = Color(0xFF1B243A)
    var InputBg = Color(0xFF12192B)

    // 极光（背景光晕）
    var AuroraPrimary = Color(0xFF0EA5E9)
    var AuroraAccent = Color(0xFF7C3AED)
    var AuroraWarm = Color(0xFFF43F5E)

    // 气泡（18/6 圆角特殊保留）
    var BubbleUser = Color(0xFF0EA5E9)
    var BubbleUserEnd = Color(0xFF6366F1)
    var BubbleAi = Color(0xFF12192B)
    var BubbleAiBorder = Color(0x1FFFFFFF)
}

/** 颜色模式（system / dark / light）→ 色板切换 */
object AppTheme {

    private data class Palette(
        val bgTop: Long, val bgBottom: Long,
        val surface: Long, val surfaceElevated: Long,
        val textPrimary: Long, val textSecondary: Long, val textTertiary: Long, val textDisabled: Long,
        val primary: Long, val primaryDeep: Long, val accent: Long, val onPrimary: Long,
        val success: Long, val warning: Long, val danger: Long,
        val divider: Long, val outline: Long, val highlight: Long,
        val glassFill: Long, val glassShade: Long, val fieldBg: Long, val fieldBgFocused: Long,
        val menuBg: Long, val sheetBg: Long, val inputBg: Long,
        val aurora1: Long, val aurora2: Long, val aurora3: Long,
        val bubbleUser: Long, val bubbleUserEnd: Long, val bubbleAi: Long, val bubbleAiBorder: Long,
    )

    private val dark = Palette(
        bgTop = 0xFF0F1526, bgBottom = 0xFF0A0E1A,
        surface = 0xFF12192B, surfaceElevated = 0xFF1B243A,
        textPrimary = 0xFFF1F5F9, textSecondary = 0xB8B6C2D4, textTertiary = 0x8C8C99AD, textDisabled = 0x615F6B80,
        primary = 0xFF0EA5E9, primaryDeep = 0xFF0284C7, accent = 0xFFF43F5E, onPrimary = 0xFFFFFFFF,
        success = 0xFF22C55E, warning = 0xFFFBBF24, danger = 0xFFF43F5E,
        divider = 0x10FFFFFF, outline = 0x1AFFFFFF, highlight = 0x29FFFFFF,
        glassFill = 0x14FFFFFF, glassShade = 0x1A000000, fieldBg = 0x0FFFFFFF, fieldBgFocused = 0x18FFFFFF,
        menuBg = 0xF21B243A, sheetBg = 0xFF1B243A, inputBg = 0xFF12192B,
        aurora1 = 0xFF0EA5E9, aurora2 = 0xFF7C3AED, aurora3 = 0xFFF43F5E,
        bubbleUser = 0xFF0EA5E9, bubbleUserEnd = 0xFF6366F1, bubbleAi = 0xFF12192B, bubbleAiBorder = 0x1FFFFFFF,
    )

    private val light = Palette(
        bgTop = 0xFFF4F8FD, bgBottom = 0xFFE3ECF8,
        surface = 0xFFFFFFFF, surfaceElevated = 0xFFF7FAFE,
        textPrimary = 0xFF0B1220, textSecondary = 0xB8475569, textTertiary = 0x8C64748B, textDisabled = 0x6194A3B8,
        primary = 0xFF0284C7, primaryDeep = 0xFF075985, accent = 0xFFE11D48, onPrimary = 0xFFFFFFFF,
        success = 0xFF16A34A, warning = 0xFFD97706, danger = 0xFFE11D48,
        divider = 0x140F172A, outline = 0x1A0F172A, highlight = 0x290F172A,
        glassFill = 0xB3FFFFFF, glassShade = 0x0F0F172A, fieldBg = 0xFFFFFFFF, fieldBgFocused = 0xFFFFFFFF,
        menuBg = 0xF7FBFEFF, sheetBg = 0xFFFFFFFF, inputBg = 0xFFFFFFFF,
        aurora1 = 0xFF38BDF8, aurora2 = 0xFFA78BFA, aurora3 = 0xFFFB7185,
        bubbleUser = 0xFF0284C7, bubbleUserEnd = 0xFF4F46E5, bubbleAi = 0xFFFFFFFF, bubbleAiBorder = 0x140F172A,
    )

    fun apply(mode: String) {
        val p = if (mode == "light") light else dark
        GlassTokens.BackgroundTop = Color(p.bgTop)
        GlassTokens.BackgroundBottom = Color(p.bgBottom)
        GlassTokens.Surface = Color(p.surface)
        GlassTokens.SurfaceElevated = Color(p.surfaceElevated)
        GlassTokens.TextPrimary = Color(p.textPrimary)
        GlassTokens.TextSecondary = Color(p.textSecondary)
        GlassTokens.TextTertiary = Color(p.textTertiary)
        GlassTokens.TextDisabled = Color(p.textDisabled)
        GlassTokens.Primary = Color(p.primary)
        GlassTokens.PrimaryDeep = Color(p.primaryDeep)
        GlassTokens.Accent = Color(p.accent)
        GlassTokens.OnPrimary = Color(p.onPrimary)
        GlassTokens.Success = Color(p.success)
        GlassTokens.Warning = Color(p.warning)
        GlassTokens.Danger = Color(p.danger)
        GlassTokens.Divider = Color(p.divider)
        GlassTokens.Outline = Color(p.outline)
        GlassTokens.Highlight = Color(p.highlight)
        GlassTokens.GlassFill = Color(p.glassFill)
        GlassTokens.GlassShade = Color(p.glassShade)
        GlassTokens.GlassBorder = Color(p.outline)
        GlassTokens.FieldBg = Color(p.fieldBg)
        GlassTokens.FieldBgFocused = Color(p.fieldBgFocused)
        GlassTokens.MenuBg = Color(p.menuBg)
        GlassTokens.SheetBg = Color(p.sheetBg)
        GlassTokens.InputBg = Color(p.inputBg)
        GlassTokens.AuroraPrimary = Color(p.aurora1)
        GlassTokens.AuroraAccent = Color(p.aurora2)
        GlassTokens.AuroraWarm = Color(p.aurora3)
        GlassTokens.BubbleUser = Color(p.bubbleUser)
        GlassTokens.BubbleUserEnd = Color(p.bubbleUserEnd)
        GlassTokens.BubbleAi = Color(p.bubbleAi)
        GlassTokens.BubbleAiBorder = Color(p.bubbleAiBorder)
    }

    /** system 模式按系统深浅色解析成 dark / light */
    fun resolve(mode: String, context: android.content.Context): String = when (mode) {
        "light" -> "light"
        "system" -> {
            val night = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            if (night) "dark" else "light"
        }
        else -> "dark"
    }
}