package com.isledeer.soullink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 世界书 / 游戏面板 —— 全工程**唯一**保留 WebView 的地方。
 *
 * 现在只是占位页：一个玻璃面板 + 返回按钮，证明路由已通。
 * 接入时的做法（不着急，等世界观 schema 定稿）：
 *
 *   AndroidView(
 *       factory = { ctx -> WebView(ctx).apply {
 *           settings.javaScriptEnabled = true
 *           settings.domStorageEnabled = true
 *           loadUrl("http://127.0.0.1:38517/worldbook.html")
 *       } },
 *       modifier = Modifier.fillMaxSize(),
 *   )
 *
 * LocalAssetServer / WebAppInterface / NativeSSE 三个类就是为这一步留的，
 * 现在都没有被引用，不影响编译。
 */
@Composable
fun WorldBookScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(GlassTokens.BackgroundTop, GlassTokens.BackgroundBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                Text(
                    text = "世界书",
                    color = GlassTokens.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .liquidGlass(cornerRadius = GlassTokens.PanelRadius),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "世界书 / 游戏面板\n\n（将来在这里挂 WebView\n加载规则卡与地图）",
                    color = GlassTokens.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}