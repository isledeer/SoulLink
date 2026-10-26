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
 * 本地工具注册表（含执行环路所需的一切）。
 *
 * executor 是 suspend：生图这类工具要发网络请求，时间/剪贴板则直接返回。
 * 工具调用往返由 ChatViewModel 驱动：
 *   模型发出 tool_calls → executor 执行 → 结果以 role:"tool" 回填 → 自动续流。
 */
data class LocalTool(
    val id: String,
    val name: String,
    val description: String,
    /** JSON Schema（OpenAI function.parameters 结构） */
    val parameters: JSONObject,
    val executor: suspend (JSONObject) -> String,
    /** 结果为图片（generate_image 返回 base64，直接写进消息 parts） */
    val outputImage: Boolean = false,
)

class LocalTools(private val context: Context) {

    /** 生图钩子：由 ChatViewModel 按当前 Provider 注入（返回 base64 或 null） */
    var imageGenerator: (suspend (String) -> String?)? = null

    val all: List<LocalTool> = listOf(timeTool(), clipboardTool(), imageTool())

    fun byId(id: String): LocalTool? = all.firstOrNull { it.id == id }

    fun byName(name: String): LocalTool? = all.firstOrNull { it.name == name }

    fun resolve(ids: List<String>): List<LocalTool> = ids.mapNotNull { byId(it) }

    private fun timeTool(): LocalTool = LocalTool(
        id = "time",
        name = "get_current_time",
        description = "获取当前日期、时间与时区。",
        parameters = JSONObject().put("type", "object").put("properties", JSONObject()),
        executor = suspend { _ ->
            val now = Date()
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val zone = TimeZone.getDefault()
            format.timeZone = zone
            "现在时间：${format.format(now)}（时区 ${zone.id}，星期${weekday(now)}）"
        },
    )

    private fun clipboardTool(): LocalTool = LocalTool(
        id = "clipboard",
        name = "clipboard_read",
        description = "读取系统剪贴板中的文本内容。",
        parameters = JSONObject().put("type", "object").put("properties", JSONObject()),
        executor = suspend { _ ->
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val text = manager?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
            text?.takeIf { it.isNotBlank() } ?: "剪贴板为空"
        },
    )

    private fun imageTool(): LocalTool = LocalTool(
        id = "image",
        name = "generate_image",
        description = "根据文字描述生成一张图片（需要运营商支持 DALL·E 3 风格生图端点）。",
        parameters = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject().put("prompt", JSONObject().put("type", "string"))
            ),
        outputImage = true,
        executor = suspend { args ->
            val prompt = args.optString("prompt")
            if (prompt.isBlank()) {
                "缺少 prompt 参数"
            } else {
                val generator = imageGenerator
                if (generator == null) {
                    "生图未配置：当前运营商不支持生图端点"
                } else {
                    generator(prompt) ?: "生图失败：检查运营商与 Key"
                }
            }
        },
    )

    private fun weekday(date: Date): String =
        SimpleDateFormat("EEEE", Locale.CHINA).format(date)

    companion object {
        /** 供 UI 展示的工具元信息（id / 名称 / 描述） */
        val catalog: List<Triple<String, String, String>> = listOf(
            Triple("time", "时间信息", "允许模型查询当前日期与时间"),
            Triple("clipboard", "剪贴板", "允许模型读取剪贴板文本"),
            Triple("image", "生图", "允许模型调用 DALL·E 风格生图（结果以图片显示）"),
        )
    }
}