package com.isledeer.soullink.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.isledeer.soullink.ui.GlassTokens
import com.isledeer.soullink.ui.LocalAppHaze
import dev.chrisbanes.haze.hazeSource
import kotlin.random.Random

/**
 * 应用背景（任务卡 4.6）：
 *   底色渐变 + 顶部 12% 主色径向光晕 + 底部 6% 强调光晕 + 2% 白噪点。
 *
 * 同时作为 haze 的 hazeSource —— 玻璃面板（liquidGlass）会真实模糊这一层。
 * 光晕缓慢漂移（24s 往复），噪点静态（固定种子，避免闪烁）。
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val haze = LocalAppHaze.current

    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aurora-drift",
    )

    val base = modifier
        .fillMaxSize()
        .background(
            Brush.verticalGradient(
                listOf(GlassTokens.BackgroundTop, GlassTokens.BackgroundBottom)
            )
        )

    Box(modifier = if (haze != null) base.hazeSource(haze) else base) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val d = drift

            // 顶部主色光晕（12%）
            val topCenter = Offset(w * (0.28f + 0.08f * d), h * (0.06f + 0.04f * d))
            val topRadius = size.minDimension * 0.9f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlassTokens.AuroraPrimary.copy(alpha = 0.12f), Color.Transparent),
                    center = topCenter,
                    radius = topRadius,
                ),
                radius = topRadius,
                center = topCenter,
            )

            // 底部强调光晕（6%）
            val bottomCenter = Offset(w * (0.55f + 0.06f * d), h * 1.02f)
            val bottomRadius = size.minDimension * 0.85f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlassTokens.AuroraWarm.copy(alpha = 0.06f), Color.Transparent),
                    center = bottomCenter,
                    radius = bottomRadius,
                ),
                radius = bottomRadius,
                center = bottomCenter,
            )

            // 2% 白噪点：固定种子，静态
            val random = Random(42)
            val noiseAlpha = 0.02f
            repeat(140) {
                val x = random.nextFloat() * w
                val y = random.nextFloat() * h
                val radius = 0.6f + random.nextFloat() * 1.0f
                drawCircle(
                    color = Color.White.copy(alpha = noiseAlpha),
                    radius = radius,
                    center = Offset(x, y),
                )
            }
        }
        content()
    }
}