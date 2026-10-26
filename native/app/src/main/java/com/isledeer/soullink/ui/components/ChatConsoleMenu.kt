package com.isledeer.soullink.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isledeer.soullink.ui.GlassTokens

private val MENU_TEXT = Color(0xFFE5E7EB)

/**
 * 右上角 ⋮ 控制台菜单（毛玻璃质感，四个区块）。
 * 事件全部由调用方（ChatScreen → ChatViewModel）绑定，未实现功能用 Toast 兜底。
 */
@Composable
fun ChatConsoleMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    tokenUsage: Int,
    autoCompress: Boolean,
    onNewConversation: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onClearConversation: () -> Unit,
    onOpenConversationSettings: () -> Unit,
    onToggleAutoCompress: () -> Unit,
    onMemory: () -> Unit,
    onSkills: () -> Unit,
    onMcp: () -> Unit,
    onMessageTree: () -> Unit,
    onRawDebug: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .background(Color(0xFF1E1E1E).copy(alpha = 0.85f), shape)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), shape),
    ) {
        /* ---------- 区块一：会话基础 ---------- */
        ConsoleItem(Icons.Outlined.Edit, "新建对话") { onNewConversation(); onDismiss() }
        ConsoleItem(Icons.Outlined.Search, "在会话中搜索") { onSearch(); onDismiss() }
        ConsoleItem(Icons.Outlined.Share, "分享 / 导出") { onShare(); onDismiss() }
        ConsoleItem(Icons.Outlined.Delete, "清空对话", danger = true) { onClearConversation(); onDismiss() }

        Divider()

        /* ---------- 区块二：参数与状态 ---------- */
        ConsoleItem(Icons.Outlined.Tune, "对话专属设置") { onOpenConversationSettings(); onDismiss() }
        ConsoleItem(
            icon = Icons.Outlined.Info,
            label = "Token 用量",
            trailing = { Text(formatTokens(tokenUsage), color = MENU_TEXT.copy(alpha = 0.65f), fontSize = 11.sp) },
            onClick = onDismiss,
        )
        ConsoleItem(
            icon = Icons.Outlined.Compress,
            label = "自动压缩",
            trailing = {
                Switch(
                    checked = autoCompress,
                    onCheckedChange = { onToggleAutoCompress() },
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = GlassTokens.Primary,
                    ),
                )
            },
            onClick = onToggleAutoCompress,
        )

        Divider()

        /* ---------- 区块三：智能体扩展 ---------- */
        ConsoleItem(Icons.Outlined.Psychology, "会话中的记忆") { onMemory(); onDismiss() }
        ConsoleItem(Icons.Outlined.Extension, "会话中的技能") { onSkills(); onDismiss() }
        ConsoleItem(Icons.Outlined.Hub, "会话中的 MCP") { onMcp(); onDismiss() }

        Divider()

        /* ---------- 区块四：极客调试 ---------- */
        ConsoleItem(Icons.Outlined.AccountTree, "查看消息树") { onMessageTree(); onDismiss() }
        ConsoleItem(Icons.Outlined.DataObject, "RAW 调试面板") { onRawDebug(); onDismiss() }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConsoleItem(
    icon: ImageVector,
    label: String,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tint = if (danger) GlassTokens.Accent else MENU_TEXT
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.scale(0.9f))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = label,
                    color = tint,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                if (trailing != null) trailing()
            }
        },
        onClick = onClick,
    )
}

private fun formatTokens(n: Int): String =
    if (n >= 1000) String.format("%.1fK", n / 1000.0) else n.toString()