package com.isledeer.soullink.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isledeer.soullink.data.AssistantEntity
import com.isledeer.soullink.tools.LocalTools
import com.isledeer.soullink.ui.GlassTokens
import com.isledeer.soullink.ui.components.CardGroup
import com.isledeer.soullink.ui.components.CardGroupItem
import com.isledeer.soullink.ui.components.Emphasized
import com.isledeer.soullink.ui.components.EmphasizedText
import com.isledeer.soullink.ui.components.FormItem
import com.isledeer.soullink.ui.components.UIAvatar
import com.isledeer.soullink.ui.liquidGlass
import org.json.JSONObject

private val PROMPT_VARIABLES = listOf("{{date}}", "{{time}}", "{{datetime}}", "{{device_info}}", "{{model}}")

/**
 * 助手详情：基础设定 / 提示词 / 本地工具 / 记忆 / 自定义请求。
 *
 * 编辑都在本地 draft 里进行，点「保存」才回写数据库 —— 避免每敲一个字就写盘。
 */
@Composable
fun AssistantDetailScreen(
    assistant: AssistantEntity,
    onBack: () -> Unit,
    onSave: (AssistantEntity) -> Unit,
) {
    var draft by remember(assistant.id) { mutableStateOf(assistant) }
    var prompt by remember(assistant.id) { mutableStateOf(assistant.systemPrompt) }
    var headersText by remember(assistant.id) { mutableStateOf(mapToLines(assistant.headerMap())) }
    var bodyText by remember(assistant.id) { mutableStateOf(mapToLines(assistant.bodyMap())) }

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
                    .heightIn(min = 52.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Text("←", color = GlassTokens.TextSecondary, fontSize = 18.sp) }

                EmphasizedText(
                    text = "助手设置",
                    style = Emphasized.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onSave(
                                draft.copy(
                                    systemPrompt = prompt,
                                    customHeaders = linesToJsonMap(headersText),
                                    customBodies = linesToJsonMap(bodyText),
                                )
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text("保存", color = GlassTokens.Primary, fontSize = 13.sp) }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp)
            ) {
                /* ---------------- 基础设定 ---------------- */
                CardGroup(title = "基础设定") {
                    CardGroupItem {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UIAvatar(text = draft.avatar ?: draft.name, size = 52.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                TextInput(
                                    value = draft.name,
                                    placeholder = "助手名称",
                                    onChange = { draft = draft.copy(name = it) },
                                )
                                Spacer(Modifier.height(6.dp))
                                TextInput(
                                    value = draft.avatar.orEmpty(),
                                    placeholder = "头像 emoji（如 🦌）",
                                    onChange = { draft = draft.copy(avatar = it.ifBlank { null }) },
                                )
                            }
                        }
                    }

                    CardGroupItem {
                        TextInput(
                            value = draft.chatModelId.orEmpty(),
                            placeholder = "绑定模型（留空 = 跟随全局默认）",
                            onChange = { draft = draft.copy(chatModelId = it.ifBlank { null }) },
                        )
                    }

                    CardGroupItem {
                        SliderRow(
                            title = "Temperature",
                            value = draft.temperature ?: 0.8f,
                            range = 0f..2f,
                            display = { ((it * 100).toInt() / 100f).toString() },
                            onChange = { draft = draft.copy(temperature = it) },
                        )
                        Spacer(Modifier.height(6.dp))
                        SliderRow(
                            title = "Top P",
                            value = draft.topP ?: 1f,
                            range = 0f..1f,
                            display = { ((it * 100).toInt() / 100f).toString() },
                            onChange = { draft = draft.copy(topP = it) },
                        )
                        Spacer(Modifier.height(6.dp))
                        SliderRow(
                            title = "Max Tokens",
                            value = (draft.maxTokens ?: 2048).toFloat(),
                            range = 256f..8192f,
                            display = { it.toInt().toString() },
                            onChange = { draft = draft.copy(maxTokens = it.toInt()) },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                /* ---------------- 提示词 ---------------- */
                CardGroup(title = "系统提示词") {
                    CardGroupItem {
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            minLines = 5,
                            maxLines = 12,
                            shape = RoundedCornerShape(14.dp),
                            placeholder = {
                                Text(
                                    "例如：你是 {{model}}，今天是 {{date}}。",
                                    color = GlassTokens.TextSecondary.copy(alpha = 0.45f),
                                    fontSize = 12.sp,
                                )
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
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PROMPT_VARIABLES.take(4).forEach { variable ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(GlassTokens.Primary.copy(alpha = 0.16f))
                                        .clickable { prompt = prompt + variable }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                ) {
                                    Text(variable, color = GlassTokens.Primary, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                /* ---------------- 本地工具 ---------------- */
                CardGroup(title = "本地工具") {
                    LocalTools.catalog.forEach { (id, title, desc) ->
                        val enabled = draft.toolList().contains(id)
                        FormItem(
                            title = title,
                            description = desc,
                            tail = {
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { checked ->
                                        val list = draft.toolList().toMutableList()
                                        if (checked) list.add(id) else list.remove(id)
                                        draft = draft.copy(localTools = toJsonArray(list))
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = GlassTokens.Primary,
                                    ),
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                /* ---------------- 记忆 ---------------- */
                CardGroup(title = "记忆") {
                    FormItem(
                        title = "长期记忆",
                        description = "开启后由助手在长对话中自动总结要点",
                        tail = {
                            Switch(
                                checked = draft.enableMemory,
                                onCheckedChange = { draft = draft.copy(enableMemory = it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = GlassTokens.Primary,
                                ),
                            )
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))

                /* ---------------- 自定义请求 ---------------- */
                CardGroup(title = "自定义请求头 / 请求体") {
                    CardGroupItem {
                        EmphasizedText(
                            text = "每行一条，格式 key: value",
                            style = Emphasized.caption,
                            color = GlassTokens.TextSecondary.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = headersText,
                            onValueChange = { headersText = it },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            placeholder = { Text("HTTP-Referer: https://…", fontSize = 12.sp, color = GlassTokens.TextSecondary.copy(alpha = 0.4f)) },
                            colors = glassFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        EmphasizedText(
                            text = "请求体（会与默认参数合并）",
                            style = Emphasized.caption,
                            color = GlassTokens.TextSecondary.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = bodyText,
                            onValueChange = { bodyText = it },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            placeholder = { Text("reasoning_effort: high", fontSize = 12.sp, color = GlassTokens.TextSecondary.copy(alpha = 0.4f)) },
                            colors = glassFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        onSave(
                            draft.copy(
                                systemPrompt = prompt,
                                customHeaders = linesToJsonMap(headersText),
                                customBodies = linesToJsonMap(bodyText),
                            )
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassTokens.Primary,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("保存", fontSize = 15.sp) }

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/* ------------------------------- 小工具 ------------------------------- */

@Composable
private fun TextInput(value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        placeholder = {
            Text(placeholder, fontSize = 12.sp, color = GlassTokens.TextSecondary.copy(alpha = 0.45f))
        },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
        colors = glassFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmphasizedText(text = title, style = Emphasized.titleSmall, modifier = Modifier.weight(1f))
            Text(display(value), color = GlassTokens.TextSecondary, fontSize = 11.sp)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
        )
    }
}

@Composable
private fun glassFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color(0x14FFFFFF),
    unfocusedContainerColor = Color(0x0DFFFFFF),
    focusedTextColor = GlassTokens.TextPrimary,
    unfocusedTextColor = GlassTokens.TextPrimary,
    cursorColor = GlassTokens.Primary,
    focusedIndicatorColor = GlassTokens.Primary.copy(alpha = 0.6f),
    unfocusedIndicatorColor = Color(0x1FFFFFFF),
)

private fun mapToLines(map: Map<String, String>): String =
    map.entries.joinToString("\n") { "${it.key}: ${it.value}" }

private fun linesToJsonMap(text: String): String {
    val json = JSONObject()
    text.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEach
        val index = trimmed.indexOf(':')
        if (index <= 0) return@forEach
        val key = trimmed.substring(0, index).trim()
        val value = trimmed.substring(index + 1).trim()
        if (key.isNotEmpty()) json.put(key, value)
    }
    return json.toString()
}

private fun toJsonArray(list: List<String>): String {
    val array = org.json.JSONArray()
    list.forEach { array.put(it) }
    return array.toString()
}