package com.isledeer.soullink.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.isledeer.soullink.data.AssistantEntity
import com.isledeer.soullink.ui.GlassTokens
import com.isledeer.soullink.ui.components.CardGroup
import com.isledeer.soullink.ui.components.CardGroupItem
import com.isledeer.soullink.ui.components.Emphasized
import com.isledeer.soullink.ui.components.EmphasizedText
import com.isledeer.soullink.ui.components.UIAvatar
import com.isledeer.soullink.ui.liquidGlass
import com.isledeer.soullink.ui.components.AuroraBackground
import androidx.compose.material.icons.automirrored.outlined.ArrowBack

/**
 * 助手列表：搜索 + 卡片列表 + 新建。
 * 每行右侧 ⋮ 弹出克隆/删除（不做长按，避免 combinedClickable 的实验 API）。
 */
@Composable
fun AssistantListScreen(
    assistants: List<AssistantEntity>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onClone: (AssistantEntity) -> Unit,
    onDelete: (String) -> Unit,
) {
    AuroraBackground(
        modifier = Modifier.fillMaxSize()
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp)
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
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = GlassTokens.TextSecondary, modifier = Modifier.size(20.dp)) }

                EmphasizedText(
                    text = "助手",
                    style = Emphasized.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                )

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCreate),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Add, contentDescription = "新建助手", tint = GlassTokens.Primary, modifier = Modifier.size(20.dp)) }
            }

            Spacer(Modifier.height(10.dp))

            // 搜索框
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(cornerRadius = 16.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = GlassTokens.TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(GlassTokens.Primary),
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                text = "搜索助手名称…",
                                color = GlassTokens.TextSecondary.copy(alpha = 0.55f),
                                fontSize = 14.sp,
                            )
                        }
                        inner()
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            val filtered = remember(assistants, query) {
                if (query.isBlank()) assistants
                else assistants.filter { it.name.contains(query, ignoreCase = true) }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    EmphasizedText(
                        text = if (assistants.isEmpty()) "还没有助手，点右上角加号新建" else "没有匹配的助手",
                        style = Emphasized.body,
                        color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    CardGroup(title = "全部助手") {
                        filtered.forEach { assistant ->
                            AssistantRow(
                                assistant = assistant,
                                onOpen = { onOpen(assistant.id) },
                                onClone = { onClone(assistant) },
                                onDelete = { onDelete(assistant.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantRow(
    assistant: AssistantEntity,
    onOpen: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }

    CardGroupItem(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UIAvatar(text = assistant.avatar ?: assistant.name, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                EmphasizedText(text = assistant.name, style = Emphasized.titleSmall)
                Spacer(Modifier.height(2.dp))
                EmphasizedText(
                    text = assistant.chatModelId?.takeIf { it.isNotBlank() } ?: "跟随全局模型",
                    style = Emphasized.caption,
                    color = GlassTokens.TextSecondary.copy(alpha = 0.6f),
                )
            }
            Box {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = GlassTokens.TextSecondary, modifier = Modifier.size(18.dp)) }

                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("克隆") },
                        onClick = { menu = false; onClone() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = GlassTokens.Accent) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}
