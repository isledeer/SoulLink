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
 * 液态玻璃面板（可替换实现）。
 *
 * ── 为什么不是真玻璃 ────────────────────────────────────────────────
 * 真实折射/色散需要 GPU shader，两条主流路线都要求高于本项目的工具链：
 *
 *   • com.github.skydoves:cloudy:1.0.0-alpha01
 *       Modifier.liquidGlass(lensCenter, lensSize, cornerRadius, refraction, curve, dispersion)
 *       要求 Kotlin 2.4.0 + Compose 1.11.4，底层 RenderEffect → API 31+
 *
 *   • io.github.kyant0:backdrop:2.0.1
 *       实际 API 是 Modifier.drawBackdrop{...} / Modifier.layerBackdrop(backdrop)
 *       （**没有** liquidGlass），要求 Kotlin 2.4.10 + Compose 1.12.0，RuntimeShader → API 33+
 *
 * 本项目当前：Kotlin 2.0.21 / Compose 1.7 / AGP 8.5.2。
 * 所以这里用「半透明纵向渐变 + 1dp 高光描边 + 深色底座」做近似，
 * 在深色背景上已有相当接近的玻璃观感。
 *
 * ── 升级路径（调用点零改动）──────────────────────────────────────────
 * 升级工具链后，把本函数的函数体换成：
 *
 *   fun Modifier.liquidGlass(...) = this.liquidGlass(
 *       lensCenter = Offset.Unspecified, // Cloudy 的签名
 *       cornerRadius = cornerRadius.toPx(),
 *       refraction = 0.6f,
 *   )
 *
 * 保留同名同参数，ChatScreen 里所有调用点一行都不用动。
 */
@Composable
fun Modifier.liquidGlass(
    cornerRadius: Dp = 24.dp,
    /** 顶部高光色 */
    highlight: Color = Color(0x26FFFFFF),
    /** 底部底座色 */
    base: Color = Color(0x0DFFFFFF),
    /** 描边强度 */
    borderAlpha: Float = 0.16f,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    highlight,
                    base,
                    Color(0x14000000),
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = borderAlpha),
                    Color.White.copy(alpha = borderAlpha * 0.30f),
                )
            ),
            shape = shape,
        )
}

/** 统一的圆角与色板，避免各处硬编码。
 *  颜色为可变变量：AppTheme.apply() 按设置切换深浅两套色板后，
 *  Activity 重建（recreate）即全界面生效。 */
object GlassTokens {
    val PanelRadius = 24.dp
    val BarRadius = 20.dp
    val PillRadius = 26.dp

    var BackgroundTop = Color(0xFF12253F)
    var BackgroundBottom = Color(0xFF070B14)

    var Primary = Color(0xFF0EA5E9)
    var PrimaryDeep = Color(0xFF0284C7)
    var Accent = Color(0xFFF43F5E)

    var TextPrimary = Color(0xFFF1F5F9)
    var TextSecondary = Color(0xFF94A3B8)

    var BubbleAi = Color(0xFF1E293B)
    var BubbleUser = Color(0xFF0284C7)
}

/** 颜色模式（system / dark / light）→ 色板切换 */
object AppTheme {

    private val dark = longArrayOf(
        // BackgroundTop / BackgroundBottom / Primary / PrimaryDeep / Accent / TextPrimary / TextSecondary / BubbleAi / BubbleUser
        0xFF12253F, 0xFF070B14, 0xFF0EA5E9, 0xFF0284C7, 0xFFF43F5E,
        0xFFF1F5F9, 0xFF94A3B8, 0xFF1E293B, 0xFF0284C7,
    )

    private val light = longArrayOf(
        0xFFEAF2FB, 0xFFCFDEEF, 0xFF0284C7, 0xFF075985, 0xFFE11D48,
        0xFF0F172A, 0xFF475569, 0xFFFFFFFF, 0xFF0284C7,
    )

    fun apply(mode: String) {
        val palette = when (mode) {
            "light" -> light
            else -> dark
        }
        GlassTokens.BackgroundTop = Color(palette[0])
        GlassTokens.BackgroundBottom = Color(palette[1])
        GlassTokens.Primary = Color(palette[2])
        GlassTokens.PrimaryDeep = Color(palette[3])
        GlassTokens.Accent = Color(palette[4])
        GlassTokens.TextPrimary = Color(palette[5])
        GlassTokens.TextSecondary = Color(palette[6])
        GlassTokens.BubbleAi = Color(palette[7])
        GlassTokens.BubbleUser = Color(palette[8])
    }
}