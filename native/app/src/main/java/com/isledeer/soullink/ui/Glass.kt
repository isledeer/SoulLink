package com.isledeer.soullink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃面板（近似实现）。
 *
 * 真实折射/色散需要 GPU shader，两条主流路线（Cloudy / backdrop）都要求
 * 更高工具链（Kotlin 2.4+ / Compose 1.11+）。这里用「多层渐变 + 高光描边 +
 * 深色底座」做近似，并把颜色全部收敛到 GlassTokens —— 深浅主题共用同一套组件。
 */
@Composable
fun Modifier.liquidGlass(
    cornerRadius: Dp = 24.dp,
    /** 顶部高光色（默认取主题 token） */
    highlight: Color = GlassTokens.GlassHighlight,
    /** 玻璃主体色 */
    base: Color = GlassTokens.GlassFill,
    /** 底部暗部色 */
    shade: Color = GlassTokens.GlassShade,
    /** 描边色 */
    border: Color = GlassTokens.GlassBorder,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                0f to highlight,
                0.42f to base,
                1f to shade,
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(border, border.copy(alpha = border.alpha * 0.25f)),
            ),
            shape = shape,
        )
}

/**
 * 全局色板。所有颜色都是可变变量：AppTheme.apply() 切换深浅两套后，
 * Activity 重建即全界面生效；组件只读 token，不再散落硬编码颜色。
 */
object GlassTokens {
    val PanelRadius = 24.dp
    val BarRadius = 20.dp
    val PillRadius = 26.dp
    val CardRadius = 18.dp

    // 背景
    var BackgroundTop = Color(0xFF0B1526)
    var BackgroundBottom = Color(0xFF03060C)

    // 极光（背景动画用）
    var AuroraPrimary = Color(0xFF0EA5E9)
    var AuroraAccent = Color(0xFF7C3AED)
    var AuroraWarm = Color(0xFFF43F5E)

    // 品牌
    var Primary = Color(0xFF22D3EE)
    var PrimaryDeep = Color(0xFF0E7490)
    var Accent = Color(0xFFF43F5E)
    var OnPrimary = Color(0xFF04121A)

    // 文本
    var TextPrimary = Color(0xFFF8FAFC)
    var TextSecondary = Color(0xFF94A3B8)
    var TextTertiary = Color(0x66B6C2D4)

    // 表面
    var Surface = Color(0xFF0F1A2B)
    var SurfaceElevated = Color(0xFF16233A)

    // 玻璃
    var GlassFill = Color(0x14FFFFFF)
    var GlassHighlight = Color(0x24FFFFFF)
    var GlassShade = Color(0x1A000000)
    var GlassBorder = Color(0x26FFFFFF)

    // 输入 / 菜单 / 弹层
    var FieldBg = Color(0x0FFFFFFF)
    var FieldBgFocused = Color(0x18FFFFFF)
    var Divider = Color(0x14FFFFFF)
    var MenuBg = Color(0xF21A2436)
    var SheetBg = Color(0xFF0B1220)
    var InputBg = Color(0xFF0F1A2B)

    // 气泡
    var BubbleUser = Color(0xFF0EA5E9)
    var BubbleUserEnd = Color(0xFF6366F1)
    var BubbleAi = Color(0xFF111C2E)
    var BubbleAiBorder = Color(0x1FFFFFFF)

    // 状态色
    var Success = Color(0xFF34D399)
    var Warning = Color(0xFFFBBF24)
    var Danger = Color(0xFFF43F5E)
}

/** 颜色模式（system / dark / light）→ 色板切换 */
object AppTheme {

    private data class Palette(
        val bgTop: Long, val bgBottom: Long,
        val aurora1: Long, val aurora2: Long, val aurora3: Long,
        val primary: Long, val primaryDeep: Long, val accent: Long, val onPrimary: Long,
        val textPrimary: Long, val textSecondary: Long, val textTertiary: Long,
        val surface: Long, val surfaceElevated: Long,
        val glassFill: Long, val glassHighlight: Long, val glassShade: Long, val glassBorder: Long,
        val fieldBg: Long, val fieldBgFocused: Long,
        val divider: Long, val menuBg: Long, val sheetBg: Long, val inputBg: Long,
        val bubbleUser: Long, val bubbleUserEnd: Long, val bubbleAi: Long, val bubbleAiBorder: Long,
        val success: Long, val warning: Long, val danger: Long,
    )

    private val dark = Palette(
        bgTop = 0xFF0B1526, bgBottom = 0xFF03060C,
        aurora1 = 0xFF0EA5E9, aurora2 = 0xFF7C3AED, aurora3 = 0xFFF43F5E,
        primary = 0xFF22D3EE, primaryDeep = 0xFF0E7490, accent = 0xFFF43F5E, onPrimary = 0xFF04121A,
        textPrimary = 0xFFF8FAFC, textSecondary = 0xFF94A3B8, textTertiary = 0x66B6C2D4,
        surface = 0xFF0F1A2B, surfaceElevated = 0xFF16233A,
        glassFill = 0x14FFFFFF, glassHighlight = 0x24FFFFFF, glassShade = 0x1A000000, glassBorder = 0x26FFFFFF,
        fieldBg = 0x0FFFFFFF, fieldBgFocused = 0x18FFFFFF,
        divider = 0x14FFFFFF, menuBg = 0xF21A2436, sheetBg = 0xFF0B1220, inputBg = 0xFF0F1A2B,
        bubbleUser = 0xFF0EA5E9, bubbleUserEnd = 0xFF6366F1, bubbleAi = 0xFF111C2E, bubbleAiBorder = 0x1FFFFFFF,
        success = 0xFF34D399, warning = 0xFFFBBF24, danger = 0xFFF43F5E,
    )

    private val light = Palette(
        bgTop = 0xFFF4F8FD, bgBottom = 0xFFE3ECF8,
        aurora1 = 0xFF38BDF8, aurora2 = 0xFFA78BFA, aurora3 = 0xFFFB7185,
        primary = 0xFF0284C7, primaryDeep = 0xFF075985, accent = 0xFFE11D48, onPrimary = 0xFFFFFFFF,
        textPrimary = 0xFF0B1220, textSecondary = 0xFF475569, textTertiary = 0x8A64748B,
        surface = 0xFFFFFFFF, surfaceElevated = 0xFFF7FAFE,
        glassFill = 0xB3FFFFFF, glassHighlight = 0xE6FFFFFF, glassShade = 0x0F0F172A, glassBorder = 0x1A0F172A,
        fieldBg = 0xFFFFFFFF, fieldBgFocused = 0xFFFFFFFF,
        divider = 0x140F172A, menuBg = 0xF7FBFEFF, sheetBg = 0xFFFFFFFF, inputBg = 0xFFFFFFFF,
        bubbleUser = 0xFF0284C7, bubbleUserEnd = 0xFF4F46E5, bubbleAi = 0xFFFFFFFF, bubbleAiBorder = 0x140F172A,
        success = 0xFF059669, warning = 0xFFD97706, danger = 0xFFE11D48,
    )

    fun apply(mode: String) {
        val p = if (mode == "light") light else dark
        GlassTokens.BackgroundTop = Color(p.bgTop)
        GlassTokens.BackgroundBottom = Color(p.bgBottom)
        GlassTokens.AuroraPrimary = Color(p.aurora1)
        GlassTokens.AuroraAccent = Color(p.aurora2)
        GlassTokens.AuroraWarm = Color(p.aurora3)
        GlassTokens.Primary = Color(p.primary)
        GlassTokens.PrimaryDeep = Color(p.primaryDeep)
        GlassTokens.Accent = Color(p.accent)
        GlassTokens.OnPrimary = Color(p.onPrimary)
        GlassTokens.TextPrimary = Color(p.textPrimary)
        GlassTokens.TextSecondary = Color(p.textSecondary)
        GlassTokens.TextTertiary = Color(p.textTertiary)
        GlassTokens.Surface = Color(p.surface)
        GlassTokens.SurfaceElevated = Color(p.surfaceElevated)
        GlassTokens.GlassFill = Color(p.glassFill)
        GlassTokens.GlassHighlight = Color(p.glassHighlight)
        GlassTokens.GlassShade = Color(p.glassShade)
        GlassTokens.GlassBorder = Color(p.glassBorder)
        GlassTokens.FieldBg = Color(p.fieldBg)
        GlassTokens.FieldBgFocused = Color(p.fieldBgFocused)
        GlassTokens.Divider = Color(p.divider)
        GlassTokens.MenuBg = Color(p.menuBg)
        GlassTokens.SheetBg = Color(p.sheetBg)
        GlassTokens.InputBg = Color(p.inputBg)
        GlassTokens.BubbleUser = Color(p.bubbleUser)
        GlassTokens.BubbleUserEnd = Color(p.bubbleUserEnd)
        GlassTokens.BubbleAi = Color(p.bubbleAi)
        GlassTokens.BubbleAiBorder = Color(p.bubbleAiBorder)
        GlassTokens.Success = Color(p.success)
        GlassTokens.Warning = Color(p.warning)
        GlassTokens.Danger = Color(p.danger)
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