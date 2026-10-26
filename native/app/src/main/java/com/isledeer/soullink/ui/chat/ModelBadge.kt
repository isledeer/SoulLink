package com.isledeer.soullink.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isledeer.soullink.ui.GlassTokens

/**
 * 模型品牌徽标：品牌色圆底 + 白色首字母（SVG→ImageVector 的完整方案留待素材
 * 库接入，按任务卡降级路径使用 Box 方案）。
 */
private val BRAND_MAP: List<Pair<List<String>, Long>> = listOf(
    listOf("openai", "gpt", "o1", "dalle", "dall-e") to 0xFF10A37F,
    listOf("claude", "anthropic") to 0xFFD97757,
    listOf("gemini", "google") to 0xFF4285F4,
    listOf("llama", "meta") to 0xFF0467DF,
    listOf("mistral") to 0xFFF54A00,
    listOf("cohere") to 0xFF39594D,
    listOf("grok", "xai") to 0xFFFFFFFF,
    listOf("perplexity") to 0xFF22B8CD,
    listOf("groq") to 0xFFF55036,
    listOf("huggingface") to 0xFFFFD21E,
    listOf("deepseek", "r1") to 0xFF4D6BFE,
    listOf("qwen", "tongyi") to 0xFF615CED,
    listOf("kimi", "moonshot") to 0xFF1883FF,
    listOf("zhipu", "glm", "chatglm") to 0xFF1B4BF9,
    listOf("minimax") to 0xFF1456F0,
    listOf("yi", "01ai") to 0xFF003425,
    listOf("ernie", "baidu") to 0xFF2932E1,
    listOf("hunyuan", "tencent") to 0xFF0052D9,
    listOf("doubao", "bytedance") to 0xFF325AB4,
    listOf("baichuan") to 0xFFFF6B00,
    listOf("stepfun", "step") to 0xFF1964FF,
    listOf("sensetime", "sense") to 0xFF000000,
    listOf("siliconflow") to 0xFF4D6BFE,
    listOf("ollama") to 0xFFFFFFFF,
    listOf("lmstudio") to 0xFF4338CA,
    listOf("midjourney", "mj") to 0xFFFFFFFF,
    listOf("stability", "sdxl", "sd") to 0xFF8B5CF6,
    listOf("replicate") to 0xFF000000,
    listOf("flux") to 0xFF000000,
)

fun brandColor(modelName: String): Color? {
    val name = modelName.lowercase()
    for ((keywords, value) in BRAND_MAP) {
        if (keywords.any { name.contains(it) }) return Color(value)
    }
    return null
}

private fun isLight(color: Color): Boolean {
    val l = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return l > 0.6f
}

@Composable
fun BrandIcon(modelName: String, size: Dp = 20.dp) {
    val color = brandColor(modelName)
    if (color == null) {
        Icon(
            Icons.Outlined.Memory,
            contentDescription = "模型",
            tint = GlassTokens.TextSecondary,
            modifier = Modifier.size(size),
        )
        return
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = modelName.trim().firstOrNull()?.uppercase() ?: "M",
            color = if (isLight(color)) Color(0xFF111111) else Color.White,
            fontSize = (size.value * 0.5f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}