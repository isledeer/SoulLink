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

/**
 * 极光动态背景：底色渐变 + 三团缓慢漂移的径向光晕。
 * 纯 Canvas 绘制，无第三方依赖；只在背景层重绘，不影响内容层。
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(GlassTokens.BackgroundTop, GlassTokens.BackgroundBottom)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val d = drift

            val topCenter = Offset(w * (0.18f + 0.10f * d), h * (0.10f + 0.05f * d))
            val topRadius = size.minDimension * 0.85f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlassTokens.AuroraPrimary.copy(alpha = 0.20f), Color.Transparent),
                    center = topCenter,
                    radius = topRadius,
                ),
                radius = topRadius,
                center = topCenter,
            )

            val sideCenter = Offset(w * (0.88f - 0.12f * d), h * (0.30f + 0.06f * d))
            val sideRadius = size.minDimension * 0.72f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlassTokens.AuroraAccent.copy(alpha = 0.16f), Color.Transparent),
                    center = sideCenter,
                    radius = sideRadius,
                ),
                radius = sideRadius,
                center = sideCenter,
            )

            val bottomCenter = Offset(w * (0.50f + 0.08f * d), h * 0.98f)
            val bottomRadius = size.minDimension * 0.9f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlassTokens.AuroraWarm.copy(alpha = 0.10f), Color.Transparent),
                    center = bottomCenter,
                    radius = bottomRadius,
                ),
                radius = bottomRadius,
                center = bottomCenter,
            )
        }
        content()
    }
}