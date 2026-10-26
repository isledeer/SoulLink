package com.isledeer.soullink.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 本地工具注册表。
 *
 * 每个工具 = 给模型的 schema + 实际执行体。
 * 注意：当前阶段只做「按需注入 schema」（Assistant.localTools 决定注入哪些），
 * 工具调用的往返执行（模型发 tool_calls → 本地执行 → 回填结果）放到下一阶段。
 * 默认助手的 localTools 为空，所以不会影响现有对话行为。
 */
data class LocalTool(
    val id: String,
    val name: String,
    val description: String,
    /** JSON Schema（OpenAI function.parameters 结构） */
    val parameters: JSONObject,
    val executor: (JSONObject) -> String,
)

class LocalTools(private val context: Context) {

    val all: List<LocalTool> = listOf(timeTool(), clipboardTool())

    fun byId(id: String): LocalTool? = all.firstOrNull { it.id == id }

    fun resolve(ids: List<String>): List<LocalTool> = ids.mapNotNull { byId(it) }

    private fun timeTool(): LocalTool = LocalTool(
        id = "time",
        name = "get_current_time",
        description = "获取当前日期、时间与时区。",
        parameters = JSONObject().put("type", "object").put("properties", JSONObject()),
    ) {
        val now = Date()
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val zone = TimeZone.getDefault()
        format.timeZone = zone
        "现在时间：${format.format(now)}（时区 ${zone.id}，星期${weekday(now)}）"
    }

    private fun clipboardTool(): LocalTool = LocalTool(
        id = "clipboard",
        name = "clipboard_read",
        description = "读取系统剪贴板中的文本内容。",
        parameters = JSONObject().put("type", "object").put("properties", JSONObject()),
    ) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = manager?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        text?.takeIf { it.isNotBlank() } ?: "剪贴板为空"
    }

    private fun weekday(date: Date): String =
        SimpleDateFormat("EEEE", Locale.CHINA).format(date)

    companion object {
        /** 供 UI 展示的工具元信息 */
        val catalog: List<Triple<String, String, String>> = listOf(
            Triple("time", "时间信息", "允许模型查询当前日期与时间"),
            Triple("clipboard", "剪贴板", "允许模型读取剪贴板文本"),
        )
    }
}