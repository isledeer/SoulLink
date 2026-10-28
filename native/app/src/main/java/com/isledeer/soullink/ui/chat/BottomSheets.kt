package com.isledeer.soullink.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.isledeer.soullink.ui.GlassTokens
import com.isledeer.soullink.util.DocumentParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* --------------------------- 附件面板 --------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachBottomSheet(
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onFillInput: (String) -> Unit,
    onCamera: () -> Unit,
    onWorldBook: () -> Unit,
    onPrompt: () -> Unit,
    onContext: () -> Unit,
    onSkills: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) { DocumentParser.extract(context, uri) }
                if (text.startsWith("（")) {
                    onFillInput("[已选择文件: ${uri.lastPathSegment ?: "文件"}]")
                } else {
                    onFillInput(text)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GlassTokens.SheetBg,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SheetHandle()

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AttachIcon(Icons.Outlined.PhotoCamera, "拍照") { onCamera() }
                AttachIcon(Icons.Outlined.Image, "照片") { onPickPhoto() }
                AttachIcon(Icons.Outlined.AttachFile, "上传文件") {
                    pickFile.launch("*/*")
                }
            }

            SheetListRow(Icons.Outlined.Extension, "技能") { onSkills() }
            SheetListRow(Icons.Outlined.MoveToInbox, "指令注入") { onPrompt() }
            SheetListRow(Icons.Outlined.MenuBook, "世界书") { onWorldBook() }
            SheetListRow(Icons.Outlined.AccountTree, "上下文管理") { onContext() }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AttachIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(GlassTokens.ControlRadius)).clickable(onClick = onClick).padding(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = GlassTokens.TextPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = GlassTokens.TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun SheetListRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassTokens.ControlRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = GlassTokens.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = GlassTokens.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = GlassTokens.TextSecondary, modifier = Modifier.size(16.dp))
    }
}

/* ---------------------------- 工具面板 ---------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsBottomSheet(
    toolStates: Map<String, Boolean>,
    onToggleTool: (String) -> Unit,
    mcpServers: List<Pair<String, String>>,
    enabledMcp: Set<String>,
    onToggleMcp: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GlassTokens.SheetBg,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SheetHandle()

            Text("工具", color = GlassTokens.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            SheetSectionTitle("本地工具")
            ToolSwitchRow(Icons.Outlined.Schedule, "时间信息", toolStates["time"] == true) { onToggleTool("time") }
            ToolSwitchRow(Icons.Outlined.ContentPaste, "剪贴板", toolStates["clipboard"] == true) { onToggleTool("clipboard") }
            ToolSwitchRow(Icons.Outlined.VolumeUp, "文字转语音", toolStates["tts"] == true) { onToggleTool("tts") }

            Spacer(Modifier.height(8.dp))
            SheetSectionTitle("MCP服务器")
            if (mcpServers.isEmpty()) {
                Text(
                    text = "还没有配置 MCP 服务器（设置 → MCP）",
                    color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            } else {
                mcpServers.forEach { (id, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(GlassTokens.ControlRadius))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Hub, contentDescription = null, tint = GlassTokens.TextSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(name, color = GlassTokens.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = id in enabledMcp,
                            onCheckedChange = { onToggleMcp(id) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToolSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassTokens.ControlRadius))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = GlassTokens.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = GlassTokens.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun SheetSectionTitle(text: String) {
    Text(text, color = GlassTokens.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .padding(top = 6.dp, bottom = 10.dp)
            .size(width = 40.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp)),
    )
}

/* --------------------------- 斜杠指令弹窗 --------------------------- */

@Composable
fun SlashCommandPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCommand: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .clip(RoundedCornerShape(GlassTokens.ControlRadius))
            .background(GlassTokens.MenuBg),
    ) {
        SlashRow("/clear", Icons.Outlined.DeleteSweep) { onCommand("/clear"); onDismiss() }
        SlashRow("/compact", Icons.Outlined.Compress) { onCommand("/compact"); onDismiss() }
        SlashRow("/memory", Icons.Outlined.Psychology) { onCommand("/memory"); onDismiss() }
        SlashRow("/thinking", Icons.Outlined.Lightbulb) { onCommand("/thinking"); onDismiss() }
    }
}

@Composable
private fun SlashRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = GlassTokens.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(icon, contentDescription = null, tint = GlassTokens.TextPrimary, modifier = Modifier.size(16.dp))
            }
        },
        onClick = onClick,
    )
}


/* ------------------------- 联网搜索面板 ------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchEngineBottomSheet(
    masterEnabled: Boolean,
    engineStates: Map<String, Boolean>,
    engineKeys: Map<String, String>,
    onMasterChange: (Boolean) -> Unit,
    onEngineChange: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val engines = listOf("bing" to "必应", "bocha" to "博查", "tavily" to "Tavily", "exa" to "Exa")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GlassTokens.SheetBg,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SheetHandle()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(GlassTokens.ControlRadius))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Public, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("启用联网搜索", color = GlassTokens.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(checked = masterEnabled, onCheckedChange = onMasterChange)
            }

            Spacer(Modifier.height(8.dp))
            SheetSectionTitle("搜索引擎")

            engines.forEach { (id, label) ->
                val hasKey = engineKeys[id].orEmpty().isNotBlank()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(GlassTokens.ControlRadius))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = GlassTokens.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (!hasKey) {
                        Text("需配置 Key", color = GlassTokens.TextSecondary.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.padding(end = 8.dp))
                    }
                    Switch(
                        checked = engineStates[id] == true,
                        onCheckedChange = { onEngineChange(id, it) },
                        enabled = hasKey,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
