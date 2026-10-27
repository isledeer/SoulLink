package com.isledeer.soullink.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.foundation.Canvas
import com.isledeer.soullink.ui.GlassTokens
import kotlin.math.roundToInt

private val LEVELS = listOf("OFF", "LOW", "MEDIUM", "HIGH", "XHIGH")

/**
 * 思考深度弹出面板：5 档自绘滑块。
 * 高仿液态玻璃：毛玻璃底 + 1dp 半透明白边 + 16dp 圆角；
 * Thumb 用 Canvas 画「白色圆球 + 主色发光圈」，轨道为渐变细条。
 */
@Composable
fun ThinkingSliderPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    current: String,
    onPick: (String) -> Unit,
) {
    if (!expanded) return

    var drag by remember(current) {
        mutableFloatStateOf(LEVELS.indexOf(current).coerceAtLeast(0).toFloat())
    }

    Popup(
        onDismissRequest = onDismiss,
        alignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .width(240.dp)
                .background(GlassTokens.MenuBg, RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Text("思考深度", color = GlassTokens.TextSecondary, fontSize = 11.sp)

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .pointerInput(current) {
                        detectTapGestures { offset ->
                            val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                            val index = (ratio * (LEVELS.size - 1)).roundToInt()
                            drag = index.toFloat()
                            onPick(LEVELS[index])
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(32.dp)) {
                    val trackY = size.height / 2f
                    val trackTop = Offset(6.dp.toPx(), trackY - 3.dp.toPx())
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0x33FFFFFF), GlassTokens.Primary.copy(alpha = 0.6f))
                        ),
                        topLeft = trackTop,
                        size = Size(size.width - 12.dp.toPx(), 6.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    )
                    val thumbX = 6.dp.toPx() + (size.width - 12.dp.toPx()) * drag / (LEVELS.size - 1)
                    val center = Offset(thumbX, trackY)
                    drawCircle(color = GlassTokens.Primary.copy(alpha = 0.28f), radius = 11.dp.toPx(), center = center)
                    drawCircle(color = Color.White, radius = 7.dp.toPx(), center = center)
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                LEVELS.forEachIndexed { index, level ->
                    val selected = LEVELS.indexOf(current) == index
                    Text(
                        text = level,
                        color = if (selected) GlassTokens.Primary else Color(0xFF9CA3AF),
                        fontSize = 9.sp,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .then(Modifier.padding(vertical = 2.dp)),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}