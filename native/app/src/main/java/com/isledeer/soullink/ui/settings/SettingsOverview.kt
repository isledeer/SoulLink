package com.isledeer.soullink.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isledeer.soullink.ui.GlassTokens
import com.isledeer.soullink.ui.bouncyClickable
import com.isledeer.soullink.ui.liquidGlass
import com.isledeer.soullink.ui.components.AuroraBackground
import androidx.compose.material.icons.automirrored.outlined.ArrowBack

/**
 * 设置中心总览页（对标 RikkaHub 分组结构）。
 * 四组 FormItem：左侧半透明圆底 Outlined 图标，右侧箭头。
 */
@Composable
fun SettingsOverviewScreen(
    colorMode: String,
    onSetColorMode: (String) -> Unit,
    onBack: () -> Unit,
    onOpenAssistants: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenSearchService: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenFeatures: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenWebServer: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSponsor: () -> Unit,
    onOpenShare: () -> Unit,
) {
    AuroraBackground(
        modifier = Modifier.fillMaxSize()
        ) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(cornerRadius = GlassTokens.BarRadius)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = GlassTokens.TextSecondary, modifier = Modifier.size(20.dp)) }
                Text("设置", color = GlassTokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp)
            ) {
                SectionLabel("通用设置")
                ColorModeRow(selected = colorMode, onSelect = onSetColorMode)
                OverviewRow(Icons.Outlined.Tune, "偏好设置", "主题、通知、界面和常规设置", onOpenPreferences)
                OverviewRow(Icons.Outlined.SmartToy, "助手管理", "设置个性化助手（智能体）", onOpenAssistants)
                OverviewRow(Icons.Outlined.Extension, "扩展管理", "管理提示词注入、技能等扩展", onOpenExtensions)

                SectionLabel("模型与服务")
                OverviewRow(Icons.Outlined.Settings, "默认模型和提示词", "设置各个功能的默认模型", onOpenFeatures)
                OverviewRow(Icons.Outlined.Dns, "提供商", "配置 AI 提供商", onOpenProviders)
                OverviewRow(Icons.Outlined.Search, "搜索服务", "设置搜索服务", onOpenSearchService)
                OverviewRow(Icons.Outlined.RecordVoiceOver, "语音服务", "配置语音合成与语音识别服务提供商", onOpenVoice)
                OverviewRow(Icons.Outlined.Image, "媒体", "图像与视频生成提供商、素材上传", onOpenMedia)
                OverviewRow(Icons.Outlined.Hub, "MCP", "配置 MCP 服务器", onOpenMcp)
                OverviewRow(Icons.Outlined.Devices, "Web 服务器", "允许你通过网页访问 SoulLink", onOpenWebServer)

                SectionLabel("数据设置")
                OverviewRow(Icons.Outlined.SaveAlt, "数据备份", "备份和恢复应用数据", onOpenBackup)
                OverviewRow(Icons.Outlined.Storage, "存储管理", "查看并清理本地文件", onOpenStorage)

                SectionLabel("关于")
                OverviewRow(Icons.Outlined.Info, "关于", "关于本 APP", onOpenAbout)
                OverviewRow(Icons.Outlined.Description, "使用文档", "查看应用使用说明和帮助文档", onOpenHelp)
                OverviewRow(Icons.Outlined.Key, "请求日志", "查看 HTTP 请求日志", onOpenLogs)
                OverviewRow(Icons.Outlined.FavoriteBorder, "赞助", "如果你喜欢这个应用，请赞助作者的工作", onOpenSponsor)
                OverviewRow(Icons.Outlined.Share, "分享", "分享本 APP 给朋友", onOpenShare)

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun OverviewRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassTokens.CardRadius))
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(GlassTokens.Divider),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = GlassTokens.TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = GlassTokens.TextPrimary, fontSize = 14.sp)
            Text(subtitle, color = GlassTokens.TextSecondary.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = GlassTokens.TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ColorModeRow(selected: String, onSelect: (String) -> Unit) {
    val modes = listOf(
        "跟随系统" to ("system" to Icons.Outlined.DarkMode),
        "浅色" to ("light" to Icons.Outlined.LightMode),
        "深色" to ("dark" to Icons.Outlined.Palette),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        modes.forEach { (label, modeAndIcon) ->
            val mode = modeAndIcon.first
            val icon = modeAndIcon.second
            val active = mode == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(GlassTokens.ControlRadius))
                    .background(if (active) GlassTokens.Primary.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = if (active) GlassTokens.Primary else GlassTokens.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.height(4.dp))
                Text(label, color = if (active) GlassTokens.Primary else GlassTokens.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

/**
 * 搜索服务配置页：四个搜索引擎的 Key 集中管理。
 */
@Composable
fun SearchServiceScreen(
    keys: Map<String, String>,
    onChangeKey: (String, String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val engines = listOf("bing" to "必应", "bocha" to "博查", "tavily" to "Tavily", "exa" to "Exa")

    AuroraBackground(
        modifier = Modifier.fillMaxSize()
        ) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(cornerRadius = GlassTokens.BarRadius)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = GlassTokens.TextSecondary, modifier = Modifier.size(20.dp)) }
                Text("搜索服务", color = GlassTokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp)
            ) {
                engines.forEach { (id, label) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .liquidGlass(cornerRadius = 18.dp)
                            .padding(12.dp)
                    ) {
                        Text(label, color = GlassTokens.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = keys[id].orEmpty(),
                            onValueChange = { onChangeKey(id, it) },
                            singleLine = true,
                            shape = RoundedCornerShape(GlassTokens.ControlRadius),
                            placeholder = {
                                Text("$label API Key", color = GlassTokens.TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp)
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0x14FFFFFF),
                                unfocusedContainerColor = Color(0x0DFFFFFF),
                                focusedTextColor = GlassTokens.TextPrimary,
                                unfocusedTextColor = GlassTokens.TextPrimary,
                                cursorColor = GlassTokens.Primary,
                                focusedIndicatorColor = GlassTokens.Primary.copy(alpha = 0.6f),
                                unfocusedIndicatorColor = Color(0x1FFFFFFF),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Button(
                    onClick = onSave,
                    shape = RoundedCornerShape(GlassTokens.CardRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassTokens.Primary,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) { Text("保存", fontSize = 15.sp) }
            }
        }
    }
}