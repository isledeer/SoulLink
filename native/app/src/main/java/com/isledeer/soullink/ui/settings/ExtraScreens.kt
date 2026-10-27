package com.isledeer.soullink.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.isledeer.soullink.data.AppSettings
import com.isledeer.soullink.data.Feature
import com.isledeer.soullink.data.McpServerConfig
import com.isledeer.soullink.data.ProviderConfig
import com.isledeer.soullink.net.RequestLog
import com.isledeer.soullink.ui.GlassTokens
import com.isledeer.soullink.ui.components.ModelPickerSheet
import com.isledeer.soullink.ui.liquidGlass

/* ----------------------------- 通用外壳 ----------------------------- */

@Composable
private fun PageShell(
    title: String,
    onBack: () -> Unit,
    content: @Composable Column.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(GlassTokens.BackgroundTop, GlassTokens.BackgroundBottom)
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(cornerRadius = GlassTokens.BarRadius)
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Text("←", color = GlassTokens.TextSecondary, fontSize = 18.sp) }
                Text(title, color = GlassTokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, color = GlassTokens.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 6.dp))
}

@Composable
internal fun SettingRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    tail: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = GlassTokens.TextPrimary, fontSize = 14.sp)
            Text(subtitle, color = GlassTokens.TextSecondary.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1)
        }
        if (tail != null) {
            Spacer(Modifier.width(12.dp))
            tail()
        }
    }
}

private val GLASS_FIELD_COLORS
    @Composable get() = TextFieldDefaults.colors(
        focusedContainerColor = Color(0x14FFFFFF),
        unfocusedContainerColor = Color(0x0DFFFFFF),
        focusedTextColor = GlassTokens.TextPrimary,
        unfocusedTextColor = GlassTokens.TextPrimary,
        cursorColor = GlassTokens.Primary,
        focusedIndicatorColor = GlassTokens.Primary.copy(alpha = 0.6f),
        unfocusedIndicatorColor = Color(0x1FFFFFFF),
    )

/* ----------------------------- 偏好设置 ----------------------------- */

@Composable
fun PreferencesScreen(
    settings: AppSettings,
    notificationsGranted: Boolean,
    onSetColorMode: (String) -> Unit,
    onSetTts: (Float, Float) -> Unit,
    onRequestNotifications: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    PageShell("偏好设置", onBack) {
        SectionLabel("颜色模式")
        val modes = listOf("跟随系统", "浅色", "深色")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            modes.forEach { label ->
                val mode = when (label) {
                    "跟随系统" -> "system"
                    "浅色" -> "light"
                    else -> "dark"
                }
                val active = settings.colorMode == mode
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) GlassTokens.Primary.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { onSetColorMode(mode) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(label, color = if (active) GlassTokens.Primary else GlassTokens.TextSecondary, fontSize = 11.sp)
                }
            }
        }

        SectionLabel("通知")
        SettingRow(
            title = "系统通知",
            subtitle = "应用更新等通知（Android 13+ 需授权）",
            tail = {
                Switch(
                    checked = notificationsGranted,
                    onCheckedChange = { onRequestNotifications() },
                )
            },
        )

        SectionLabel("语音合成")
        Text(
            text = "语速 ${String.format("%.1f", settings.ttsRate)}×",
            color = GlassTokens.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp),
        )
        Slider(
            value = settings.ttsRate,
            onValueChange = { onSetTts(it, settings.ttsPitch) },
            valueRange = 0.5f..2.0f,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Text(
            text = "音调 ${String.format("%.1f", settings.ttsPitch)}",
            color = GlassTokens.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp),
        )
        Slider(
            value = settings.ttsPitch,
            onValueChange = { onSetTts(settings.ttsRate, it) },
            valueRange = 0.5f..2.0f,
            modifier = Modifier.padding(horizontal = 10.dp),
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSave,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(46.dp),
        ) { Text("保存", fontSize = 14.sp) }
        Spacer(Modifier.height(24.dp))
    }
}

/* --------------------------- 默认模型和提示词 --------------------------- */

@Composable
fun FeatureBindingsScreen(
    settings: AppSettings,
    onChangePrompt: (String) -> Unit,
    onSetBinding: (Feature, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var pickerFeature by remember { mutableStateOf<Feature?>(null) }
    PageShell("默认模型和提示词", onBack) {
        SectionLabel("全局默认提示词")
        var prompt by remember(settings.defaultPrompt) { mutableStateOf(settings.defaultPrompt) }
        OutlinedTextField(
            value = prompt,
            onValueChange = {
                prompt = it
                onChangePrompt(it)
            },
            minLines = 3,
            maxLines = 8,
            shape = RoundedCornerShape(14.dp),
            placeholder = { Text("所有对话的兜底系统提示词（可被助手 / 对话覆盖）", fontSize = 12.sp, color = GlassTokens.TextSecondary.copy(alpha = 0.5f)) },
            colors = GLASS_FIELD_COLORS,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel("功能模型绑定")
        Feature.entries.forEach { feature ->
            val binding = settings.bindings[feature]
            SettingRow(
                title = "${feature.label}模型",
                subtitle = binding?.model ?: "跟随对话默认",
                onClick = { pickerFeature = feature },
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    pickerFeature?.let { feature ->
        ModelPickerSheet(
            providers = settings.providers,
            onPick = { provider, model ->
                onSetBinding(feature, provider.id, model)
                pickerFeature = null
            },
            onDismiss = { pickerFeature = null },
        )
    }
}

/* ------------------------------- 媒体 ------------------------------- */

@Composable
fun MediaScreen(
    settings: AppSettings,
    onSetBinding: (Feature, String, String) -> Unit,
    onBack: () -> Unit,
) {
    var pickerVisible by remember { mutableStateOf(false) }
    PageShell("媒体", onBack) {
        SectionLabel("图像生成")
        val binding = settings.bindings[Feature.IMAGE]
        SettingRow(
            title = "生图 Provider 与模型",
            subtitle = binding?.model ?: "未绑定（回落到对话 Provider）",
            onClick = { pickerVisible = true },
        )
        Text(
            text = "• 助手开启「生图」工具后，生成图片走这里绑定的 Provider\n• 未绑定时使用对话 Provider 的 /v1/images/generations 端点",
            color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp, top = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
    if (pickerVisible) {
        ModelPickerSheet(
            providers = settings.providers,
            onPick = { provider, model ->
                onSetBinding(Feature.IMAGE, provider.id, model)
                pickerVisible = false
            },
            onDismiss = { pickerVisible = false },
        )
    }
}

/* ------------------------------ MCP 服务器 ------------------------------ */

@Composable
fun McpServersScreen(
    servers: List<McpServerConfig>,
    onAdd: (McpServerConfig) -> Unit,
    onUpdate: (McpServerConfig) -> Unit,
    onRemove: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<McpServerConfig?>(null) }
    var adding by remember { mutableStateOf(false) }

    PageShell("MCP", onBack) {
        Text(
            text = "MCP（Model Context Protocol）服务器：提供外部工具给模型调用。目前支持 HTTP 端点。",
            color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp),
        )
        Spacer(Modifier.height(8.dp))

        servers.forEach { server ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .liquidGlass(cornerRadius = 16.dp)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(server.name.ifBlank { "未命名" }, color = GlassTokens.TextPrimary, fontSize = 13.sp)
                    Text(server.url, color = GlassTokens.TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1)
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { editing = server },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = GlassTokens.TextSecondary, modifier = Modifier.size(16.dp)) }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onRemove(server.id) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = GlassTokens.Accent, modifier = Modifier.size(16.dp)) }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GlassTokens.Primary.copy(alpha = 0.16f))
                .clickable { adding = true }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = GlassTokens.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加服务器", color = GlassTokens.Primary, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSave,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(46.dp),
        ) { Text("保存", fontSize = 14.sp) }
        Spacer(Modifier.height(24.dp))
    }

    if (adding || editing != null) {
        McpEditDialog(
            initial = editing,
            onConfirm = { server ->
                if (editing != null) onUpdate(server) else onAdd(server)
                adding = false
                editing = null
            },
            onDismiss = {
                adding = false
                editing = null
            },
        )
    }
}

@Composable
private fun McpEditDialog(
    initial: McpServerConfig?,
    onConfirm: (McpServerConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var key by remember { mutableStateOf(initial?.apiKey.orEmpty()) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassTokens.BackgroundBottom.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text(if (initial == null) "添加 MCP 服务器" else "编辑 MCP 服务器", color = GlassTokens.TextPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("名称", fontSize = 12.sp, color = GlassTokens.TextSecondary.copy(alpha = 0.5f)) },
                colors = GLASS_FIELD_COLORS,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("HTTP 端点（如 https://mcp.example.com/mcp）", fontSize = 12.sp, color = GlassTokens.TextSecondary.copy(alpha = 0.5f)) },
                colors = GLASS_FIELD_COLORS,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("API Key（可选）", fontSize = 12.sp, color = GlassTokens.TextSecondary.copy(alpha = 0.5f)) },
                colors = GLASS_FIELD_COLORS,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消", color = GlassTokens.TextSecondary) }
                TextButton(
                    onClick = {
                        val trimmedUrl = url.trim()
                        if (name.trim().isNotBlank() && trimmedUrl.isNotBlank()) {
                            onConfirm(
                                McpServerConfig(
                                    id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    url = trimmedUrl,
                                    apiKey = key.trim(),
                                )
                            )
                        }
                    },
                ) { Text("保存", color = GlassTokens.Primary) }
            }
        }
    }
}

/* ------------------------------- 关于 ------------------------------- */

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    PageShell("关于", onBack) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(GlassTokens.Primary, GlassTokens.Accent))),
                contentAlignment = Alignment.Center,
            ) { Text("S", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp))
            Text("SoulLink", color = GlassTokens.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("本地 AI 游戏引擎 · 纯本地无中转", color = GlassTokens.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "你带角色卡与世界书，你带 API Key。\nSoulLink 只做游戏机，不做游戏。\n数据全部保存在本机。",
                color = GlassTokens.TextSecondary.copy(alpha = 0.8f),
                fontSize = 12.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(16.dp))
            SettingRow(
                title = "GitHub 仓库",
                subtitle = "isledeer/SoulLink · 查看源码与最新 Release",
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/isledeer/SoulLink")))
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ------------------------------ 使用文档 ------------------------------ */

@Composable
fun HelpScreen(onBack: () -> Unit) {
    PageShell("使用文档", onBack) {
        HelpSection("开始使用") {
            Text("1. 设置 → 提供商：填一个 OpenAI 兼容的 Base URL 与 Key，点测试连通性，保存。\n2. 设置 → 默认模型和提示词：为对话绑定模型。\n3. 回聊天页发送消息即可。", color = GlassTokens.TextSecondary, fontSize = 12.sp, lineHeight = 19.sp)
        }
        HelpSection("助手") {
            Text("助手 = 一个智能体配置：模型、温度、系统提示词、本地工具、MCP、记忆。\n聊天页顶栏可切换当前助手的模型；输入栏底部的「工具」按钮开关本地工具。", color = GlassTokens.TextSecondary, fontSize = 12.sp, lineHeight = 19.sp)
        }
        HelpSection("斜杠指令") {
            Text("/clear 清空对话\n/compact 把当前上下文压缩成记忆后清空\n/memory 查看会话记忆", color = GlassTokens.TextSecondary, fontSize = 12.sp, lineHeight = 19.sp)
        }
        HelpSection("联网搜索") {
            Text("设置 → 搜索服务填入引擎 Key（必应 / 博查 / Tavily / Exa 任一）。输入栏地球图标开启后，每轮请求会自动带上搜索结果。", color = GlassTokens.TextSecondary, fontSize = 12.sp, lineHeight = 19.sp)
        }
        HelpSection("世界书（游戏模式）") {
            Text("世界书 = 世界观设定（规则文档 + 关键词条目）。创建后点「开始游戏」进入 KP 叙事模式，AI 会给出 ABCD 选项推进剧情，状态面板记录好感度 / 背包 / 场景。", color = GlassTokens.TextSecondary, fontSize = 12.sp, lineHeight = 19.sp)
        }
        HelpSection("隐私") {
            Text("所有 Key、消息、存档只保存在本机。SoulLink 不经过任何中转服务器。", color = GlassTokens.TextSecondary, fontSize = 12.sp, lineHeight = 19.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HelpSection(title: String, content: @Composable Column.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        SectionLabel(title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(cornerRadius = 18.dp)
                .padding(14.dp),
            content = content,
        )
    }
}

/* ------------------------------ 请求日志 ------------------------------ */

@Composable
fun RequestLogScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val entries = RequestLog.snapshot()
    PageShell("请求日志", onBack) {
        Text(
            text = "最近请求（最新在前，最多 200 条）",
            color = GlassTokens.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { clipboard.setText(AnnotatedString(entries.joinToString("\n"))) }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text("复制全部", color = GlassTokens.Primary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }
        entries.forEach { line ->
            Text(
                text = line,
                color = GlassTokens.TextSecondary,
                fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
            )
        }
        if (entries.isEmpty()) {
            Text("还没有请求记录", color = GlassTokens.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(14.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/* ------------------------------- 赞助 ------------------------------- */

@Composable
fun SponsorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    PageShell("赞助", onBack) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(24.dp))
            Text("喜欢 SoulLink 吗？", color = GlassTokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "最好的支持是给 GitHub 仓库点一颗 ⭐\n并把它分享给朋友。",
                color = GlassTokens.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/isledeer/SoulLink")))
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) { Text("去 GitHub 支持", fontSize = 14.sp) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ------------------------------- 分享 ------------------------------- */

@Composable
fun ShareScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val url = "https://github.com/isledeer/SoulLink"
    PageShell("分享", onBack) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(12.dp))
            qrBitmap(url)?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "二维码",
                    modifier = Modifier.size(200.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(url, color = GlassTokens.TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "SoulLink —— 本地 AI 游戏引擎 $url")
                        }
                        context.startActivity(Intent.createChooser(intent, "分享 SoulLink"))
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary, contentColor = Color.White),
                ) { Text("系统分享", fontSize = 13.sp) }
                Button(
                    onClick = { clipboard.setText(AnnotatedString(url)) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary.copy(alpha = 0.2f), contentColor = GlassTokens.Primary),
                ) { Text("复制链接", fontSize = 13.sp) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ------------------------------ 语音服务 ------------------------------ */

@Composable
fun VoiceScreen(
    settings: AppSettings,
    onSetTts: (Float, Float) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val speaker = remember { com.isledeer.soullink.util.Speaker(context.applicationContext) }
    PageShell("语音服务", onBack) {
        SectionLabel("语音合成（朗读 / TTS 工具）")
        Text(
            text = "语速 ${String.format("%.1f", settings.ttsRate)}×",
            color = GlassTokens.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp),
        )
        Slider(
            value = settings.ttsRate,
            onValueChange = { onSetTts(it, settings.ttsPitch) },
            valueRange = 0.5f..2.0f,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Text(
            text = "音调 ${String.format("%.1f", settings.ttsPitch)}",
            color = GlassTokens.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp),
        )
        Slider(
            value = settings.ttsPitch,
            onValueChange = { onSetTts(settings.ttsRate, it) },
            valueRange = 0.5f..2.0f,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Button(
            onClick = {
                speaker.configure(settings.ttsRate, settings.ttsPitch)
                speaker.speak("你好，这是 SoulLink 的语音合成测试。")
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary.copy(alpha = 0.2f), contentColor = GlassTokens.Primary),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("试听", fontSize = 13.sp) }

        SectionLabel("语音识别")
        Text(
            text = "语音输入使用系统语音识别引擎（聊天输入栏麦克风按钮）。",
            color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp),
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSave,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(46.dp),
        ) { Text("保存", fontSize = 14.sp) }
        Spacer(Modifier.height(24.dp))
    }
}

/* ------------------------------ 扩展管理 ------------------------------ */

@Composable
fun ExtensionsScreen(
    onOpenFeatures: () -> Unit,
    onOpenAssistants: () -> Unit,
    onBack: () -> Unit,
) {
    PageShell("扩展管理", onBack) {
        Text(
            text = "扩展 = 提示词注入、助手技能与 MCP 工具。",
            color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 14.dp),
        )
        Spacer(Modifier.height(8.dp))
        SettingRow(
            title = "提示词注入",
            subtitle = "全局默认提示词（所有对话兜底）",
            onClick = onOpenFeatures,
        )
        SettingRow(
            title = "助手技能",
            subtitle = "每个助手的系统提示词、本地工具与技能开关",
            onClick = onOpenAssistants,
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** 生成二维码位图（ZXing core，项目已依赖） */
private fun qrBitmap(content: String, size: Int = 512): android.graphics.Bitmap? = runCatching {
    val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
        content, com.google.zxing.BarcodeFormat.QR_CODE, size, size
    )
    android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
    }
}.getOrNull()