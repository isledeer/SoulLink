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

    /** 生图钩子：由 ChatViewModel 按当前 Provider 注入（返回 base64 或 null）。
     *  非 suspend：executor 统一是普通 lambda，suspend 调用在注入侧用 runBlocking 消化。 */
    var imageGenerator: ((String) -> String?)? = null

    val all: List<LocalTool> = listOf(timeTool(), clipboardTool(), imageTool())

    fun byId(id: String): LocalTool? = all.firstOrNull { it.id == id }

    fun byName(name: String): LocalTool? = all.firstOrNull { it.name == name }

    fun resolve(ids: List<String>): List<LocalTool> = ids.mapNotNull { byId(it) }

    private fun timeTool(): LocalTool = LocalTool(
        id = "time",
        name = "get_current_time",
        description = "获取当前日期、时间与时区。",
        parameters = JSONObject().put("type", "object").put("properties", JSONObject()),
        executor = { args: JSONObject ->
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
        executor = { args: JSONObject ->
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
        executor = { args: JSONObject ->
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

    /**
     * 游戏状态工具（M1 只注册，不开放给普通聊天：catalog 里暂不出现）。
     * 真正的解析与落账由 ChatViewModel / GameLedgerRepository 完成，
     * 这里执行器只做占位回执。
     */
    private fun gameTool(): LocalTool = LocalTool(
        id = GAME_TOOL_ID,
        name = GAME_TOOL_NAME,
        description = "记录本轮回复造成的游戏状态变化：好感度、物品、场景、旗标、属性、时间与选项。每次回复都应调用；没有变化时传空对象。",
        parameters = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put(
                        "relation",
                        JSONObject()
                            .put("type", "object")
                            .put("description", "好感度变化")
                            .put(
                                "properties",
                                JSONObject()
                                    .put("target", JSONObject().put("type", "string"))
                                    .put("delta", JSONObject().put("type", "integer"))
                                    .put("reason", JSONObject().put("type", "string"))
                            )
                    )
                    .put(
                        "items_gained",
                        JSONObject()
                            .put("type", "array")
                            .put(
                                "items",
                                JSONObject()
                                    .put("type", "object")
                                    .put(
                                        "properties",
                                        JSONObject()
                                            .put("id", JSONObject().put("type", "string"))
                                            .put("name", JSONObject().put("type", "string"))
                                            .put("qty", JSONObject().put("type", "integer"))
                                    )
                            )
                    )
                    .put(
                        "items_lost",
                        JSONObject()
                            .put("type", "array")
                            .put(
                                "items",
                                JSONObject()
                                    .put("type", "object")
                                    .put(
                                        "properties",
                                        JSONObject()
                                            .put("id", JSONObject().put("type", "string"))
                                            .put("qty", JSONObject().put("type", "integer"))
                                    )
                            )
                    )
                    .put("scene", JSONObject().put("type", "string"))
                    .put("flags", JSONObject().put("type", "object"))
                    .put("stats", JSONObject().put("type", "object"))
                    .put("time", JSONObject().put("type", "string"))
                    .put(
                        "choices",
                        JSONObject()
                            .put("type", "array")
                            .put("items", JSONObject().put("type", "string"))
                    )
            ),
        executor = { args: JSONObject -> "已记录" },
    )

    private fun weekday(date: Date): String =
        SimpleDateFormat("EEEE", Locale.CHINA).format(date)

    companion object {
        const val GAME_TOOL_ID = "game"
        const val GAME_TOOL_NAME = "update_game_state"

        /** 供 UI 展示的工具元信息（id / 名称 / 描述） */
        val catalog: List<Triple<String, String, String>> = listOf(
            Triple("time", "时间信息", "允许模型查询当前日期与时间"),
            Triple("clipboard", "剪贴板", "允许模型读取剪贴板文本"),
            Triple("image", "生图", "允许模型调用 DALL·E 风格生图（结果以图片显示）"),
        )
    }
}