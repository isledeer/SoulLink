package com.isledeer.soullink.ui.chat

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MENU_TEXT = Color(0xFFE5E7EB)

/**
 * 顶栏 ⋮ 下拉菜单（15 项，严格按任务卡顺序与分组）。
 * 外层已由 ChatTopBar 的 Box 包裹，触发点不会乱飞。
 */
@Composable
fun TopBarDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    tokenUsage: Int,
    autoCompress: Boolean,
    onNewChat: () -> Unit,
    onClearContext: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleAutoCompress: () -> Unit,
) {
    val context = LocalContext.current
    fun toast(text: String) {
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
    }

    val shape = RoundedCornerShape(16.dp)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .background(Color(0xE61E1E1E), shape)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), shape),
    ) {
        MenuRow(Icons.Outlined.Edit, "新建对话") { onNewChat(); onDismiss() }
        MenuRow(Icons.Outlined.Search, "在会话中搜索") { toast("开发中"); onDismiss() }
        MenuRow(Icons.Outlined.Share, "分享/导出") { toast("开发中"); onDismiss() }
        MenuRow(Icons.Outlined.Delete, "清空对话", danger = true) { onClearContext(); onDismiss() }

        MenuDivider()

        MenuRow(Icons.Outlined.Tune, "对话专属设置") { onOpenSettings(); onDismiss() }
        MenuRow(
            icon = Icons.Outlined.Info,
            label = "Token 用量",
            trailing = { Text("${tokenUsage}", color = MENU_TEXT.copy(alpha = 0.65f), fontSize = 11.sp) },
            onClick = { },
        )
        MenuRow(
            icon = Icons.Outlined.Compress,
            label = "自动压缩",
            trailing = {
                Switch(
                    checked = autoCompress,
                    onCheckedChange = { onToggleAutoCompress() },
                    modifier = Modifier.scale(0.8f),
                )
            },
            onClick = onToggleAutoCompress,
        )

        MenuDivider()

        MenuRow(Icons.Outlined.Psychology, "会话中的记忆") { toast("开发中"); onDismiss() }
        MenuRow(Icons.Outlined.Extension, "会话中的技能") { toast("开发中"); onDismiss() }
        MenuRow(Icons.Outlined.Hub, "会话中的 MCP") { toast("开发中"); onDismiss() }

        MenuDivider()

        MenuRow(Icons.Outlined.AccountTree, "查看消息树") { toast("开发中"); onDismiss() }
        MenuRow(Icons.Outlined.DataObject, "RAW 调试面板") { toast("开发中"); onDismiss() }
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tint = if (danger) Color(0xFFF87171) else MENU_TEXT
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.scale(0.9f))
                Spacer(Modifier.width(10.dp))
                Text(text = label, color = tint, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (trailing != null) trailing()
            }
        },
        onClick = onClick,
    )
}