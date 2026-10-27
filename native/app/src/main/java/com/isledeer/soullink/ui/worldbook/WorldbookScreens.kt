package com.isledeer.soullink.ui.worldbook

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.isledeer.soullink.data.WorldBookEntity
import com.isledeer.soullink.data.WorldDoc
import com.isledeer.soullink.data.WorldEntryEntity
import com.isledeer.soullink.data.WorldbookTemplates
import com.isledeer.soullink.ui.GlassTokens
import com.isledeer.soullink.ui.liquidGlass
import com.isledeer.soullink.ui.settings.PageShell
import com.isledeer.soullink.ui.settings.SectionLabel
import org.json.JSONArray
import org.json.JSONObject

/* ----------------------------- 世界书列表 ----------------------------- */

@Composable
fun WorldBookListScreen(
    books: List<WorldBookEntity>,
    onBack: () -> Unit,
    onCreate: (String, String) -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onStart: (String) -> Unit,
    onImport: (String) -> Unit,
) {
    var createVisible by remember { mutableStateOf(false) }
    var importVisible by remember { mutableStateOf(false) }

    PageShell("世界书", onBack) {
        Text(
            text = "世界书 = 世界观设定（规则 + 关键词条目）。点「开始游戏」进入 KP 叙事模式，AI 给 ABCD 选项，状态账本记录好感度/背包/场景。",
            color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
            fontSize = 11.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp),
        )
        Spacer(Modifier.height(8.dp))

        books.forEach { book ->
            val doc = remember(book.docJson) { WorldDoc.parse(book.docJson) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .liquidGlass(cornerRadius = 18.dp)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(book.title, color = GlassTokens.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "${WorldbookTemplates.label(book.template)} · ${doc.npcs.size} 个 NPC",
                            color = GlassTokens.TextSecondary.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                        )
                    }
                    Text(
                        text = "开始",
                        color = GlassTokens.Primary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(GlassTokens.Primary.copy(alpha = 0.14f))
                            .clickable { onStart(book.id) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "编辑",
                        color = GlassTokens.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpen(book.id) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    Text(
                        text = "删除",
                        color = GlassTokens.Accent,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDelete(book.id) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GlassTokens.Primary.copy(alpha = 0.16f))
                .clickable { createVisible = true }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) { Text("新建世界书", color = GlassTokens.Primary, fontSize = 13.sp) }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .clickable { importVisible = true }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) { Text("导入世界书 JSON", color = GlassTokens.TextSecondary, fontSize = 13.sp) }

        Spacer(Modifier.height(24.dp))
    }

    if (createVisible) {
        CreateWorldDialog(
            onConfirm = { template, title ->
                onCreate(template, title)
                createVisible = false
            },
            onDismiss = { createVisible = false },
        )
    }
    if (importVisible) {
        ImportWorldDialog(
            onConfirm = { raw ->
                onImport(raw)
                importVisible = false
            },
            onDismiss = { importVisible = false },
        )
    }
}

@Composable
private fun CreateWorldDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var template by remember { mutableStateOf("cultivation") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassTokens.BackgroundBottom.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text("新建世界书", color = GlassTokens.TextPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("世界名（可留空）", fontSize = 12.sp, color = GlassTokens.TextSecondary.copy(alpha = 0.5f)) },
                colors = worldFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WorldbookTemplates.templates.forEach { (id, label) ->
                    val active = template == id
                    Text(
                        text = label,
                        color = if (active) GlassTokens.Primary else GlassTokens.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) GlassTokens.Primary.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
                            .clickable { template = id }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消", color = GlassTokens.TextSecondary) }
                TextButton(onClick = { onConfirm(template, title) }) { Text("创建", color = GlassTokens.Primary) }
            }
        }
    }
}

@Composable
private fun ImportWorldDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var raw by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassTokens.BackgroundBottom.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text("导入世界书", color = GlassTokens.TextPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("粘贴世界书 JSON", fontSize = 12.sp, color = GlassTokens.TextSecondary.copy(alpha = 0.5f)) },
                colors = worldFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消", color = GlassTokens.TextSecondary) }
                TextButton(onClick = { onConfirm(raw) }) { Text("导入", color = GlassTokens.Primary) }
            }
        }
    }
}

/* ----------------------------- 世界书编辑 ----------------------------- */

@Composable
fun WorldBookEditScreen(
    book: WorldBookEntity,
    entries: List<WorldEntryEntity>,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (WorldBookEntity, List<WorldEntryEntity>) -> Unit,
    onAiFill: (String) -> Unit,
    onStart: () -> Unit,
    notice: String? = null,
) {
    val context = LocalContext.current
    val doc = remember(book.id, book.docJson) { WorldDoc.parse(book.docJson) }

    var title by remember(book.id) { mutableStateOf(doc.title.ifBlank { book.title }) }
    var premise by remember(book.id) { mutableStateOf(doc.premise) }
    var setting by remember(book.id) { mutableStateOf(doc.setting) }
    var rules by remember(book.id) { mutableStateOf(doc.rules.joinToString("\n")) }
    var tone by remember(book.id) { mutableStateOf(doc.tone) }
    var scene by remember(book.id) { mutableStateOf(doc.openingScene) }
    var situation by remember(book.id) { mutableStateOf(doc.openingSituation) }
    var advancedText by remember(book.id) { mutableStateOf(book.docJson) }
    var localEntries by remember(book.id, entries) { mutableStateOf(entries) }
    var entryEditing by remember { mutableStateOf<WorldEntryEntity?>(null) }
    var addingEntry by remember { mutableStateOf(false) }
    var advancedVisible by remember { mutableStateOf(false) }
    var localNotice by remember { mutableStateOf<String?>(null) }

    fun currentBook(): WorldBookEntity {
        val rulesList = rules.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val updatedDoc = WorldbookTemplates.updateDoc(
            original = advancedText,
            title = title,
            premise = premise,
            setting = setting,
            rules = rulesList,
            tone = tone,
            openingScene = scene,
            openingSituation = situation,
        )
        return book.copy(title = title.ifBlank { book.title }, docJson = updatedDoc)
    }

    PageShell("世界书 · ${book.title}", onBack) {
        SectionLabel("基本信息")
        WorldField("世界名", title) { title = it }
        WorldField("一句前提", premise) { premise = it }
        WorldField("世界设定", setting, minLines = 3) { setting = it }
        WorldField("硬规则（每行一条）", rules, minLines = 3) { rules = it }
        WorldField("叙事基调", tone) { tone = it }
        WorldField("开场场景", scene) { scene = it }
        WorldField("开场事件", situation, minLines = 2) { situation = it }

        SectionLabel("NPC（只读，高级 JSON 可编辑）")
        if (doc.npcs.isEmpty()) {
            Text(
                text = "还没有 NPC。用 AI 填充生成，或在高级 JSON 里添加 entities.npcs。",
                color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 14.dp),
            )
        } else {
            doc.npcs.forEach { npc ->
                val roleSuffix = if (npc.role.isNotBlank()) "（" + npc.role + "）" else ""
                Text(
                    text = "• " + npc.name + roleSuffix + " · 初始好感 " + npc.initialRelation,
                    color = GlassTokens.TextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 14.dp, top = 2.dp),
                )
            }
        }

        SectionLabel("关键词条目")
        localEntries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .liquidGlass(cornerRadius = 14.dp)
                    .clickable { entryEditing = entry }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (entry.alwaysOn) "常驻" else entry.keysJson,
                        color = GlassTokens.TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                    Text(entry.content.take(60), color = GlassTokens.TextPrimary, fontSize = 11.sp, maxLines = 2)
                }
                Text(
                    text = "删",
                    color = GlassTokens.Accent,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { localEntries = localEntries.filterNot { it.id == entry.id } }
                        .padding(6.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .clickable { addingEntry = true }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) { Text("添加条目", color = GlassTokens.TextSecondary, fontSize = 12.sp) }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onAiFill(premise) },
            enabled = !busy,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary.copy(alpha = 0.2f), contentColor = GlassTokens.Primary),
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) { Text(if (busy) "AI 填充中…" else "AI 填充（按当前前提生成世界观）", fontSize = 13.sp) }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSave(currentBook(), localEntries) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary, contentColor = Color.White),
                modifier = Modifier.weight(1f).height(46.dp),
            ) { Text("保存", fontSize = 14.sp) }
            Button(
                onClick = { onStart() },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary.copy(alpha = 0.2f), contentColor = GlassTokens.Primary),
                modifier = Modifier.weight(1f).height(46.dp),
            ) { Text("开始游戏", fontSize = 14.sp) }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { advancedVisible = true }) { Text("高级 JSON", color = GlassTokens.TextSecondary, fontSize = 12.sp) }
            TextButton(
                onClick = {
                    val export = buildExportJson(currentBook().docJson, localEntries)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, export)
                    }
                    context.startActivity(Intent.createChooser(intent, "导出世界书"))
                },
            ) { Text("导出", color = GlassTokens.TextSecondary, fontSize = 12.sp) }
        }
        (notice ?: localNotice)?.let {
            Text(it, color = GlassTokens.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 14.dp))
        }
        Spacer(Modifier.height(24.dp))
    }

    if (addingEntry || entryEditing != null) {
        EntryDialog(
            initial = entryEditing,
            onConfirm = { entry ->
                if (entryEditing != null) {
                    localEntries = localEntries.map { if (it.id == entry.id) entry else it }
                } else {
                    localEntries = localEntries + entry
                }
                addingEntry = false
                entryEditing = null
            },
            onDismiss = {
                addingEntry = false
                entryEditing = null
            },
        )
    }

    if (advancedVisible) {
        var text by remember { mutableStateOf(advancedText) }
        Dialog(onDismissRequest = { advancedVisible = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassTokens.BackgroundBottom.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Text("高级 JSON", color = GlassTokens.TextPrimary, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 8,
                    maxLines = 14,
                    shape = RoundedCornerShape(12.dp),
                    colors = worldFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { advancedVisible = false }) { Text("取消", color = GlassTokens.TextSecondary) }
                    TextButton(
                        onClick = {
                            val parsed = runCatching { JSONObject(text) }.getOrNull()
                            if (parsed == null) {
                                localNotice = "JSON 不合法"
                            } else {
                                advancedText = text
                                val updated = WorldDoc.parse(text)
                                title = updated.title.ifBlank { title }
                                premise = updated.premise
                                setting = updated.setting
                                rules = updated.rules.joinToString("\n")
                                tone = updated.tone
                                scene = updated.openingScene
                                situation = updated.openingSituation
                                advancedVisible = false
                            }
                        },
                    ) { Text("应用", color = GlassTokens.Primary) }
                }
            }
        }
    }
}

@Composable
private fun EntryDialog(
    initial: WorldEntryEntity?,
    onConfirm: (WorldEntryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var keys by remember { mutableStateOf(initial?.keysJson.orEmpty()) }
    var content by remember { mutableStateOf(initial?.content.orEmpty()) }
    var alwaysOn by remember { mutableStateOf(initial?.alwaysOn ?: false) }
    var priority by remember { mutableStateOf((initial?.priority ?: 0).toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassTokens.BackgroundBottom.copy(alpha = 0.97f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Text(if (initial == null) "添加条目" else "编辑条目", color = GlassTokens.TextPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            WorldField("触发关键词（逗号分隔，可空）", keys) { keys = it }
            WorldField("条目内容", content, minLines = 3) { content = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("常驻注入", color = GlassTokens.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = alwaysOn, onCheckedChange = { alwaysOn = it })
            }
            WorldField("优先级（越大越优先）", priority) { priority = it }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消", color = GlassTokens.TextSecondary) }
                TextButton(
                    onClick = {
                        if (content.isNotBlank()) {
                            val keysArray = JSONArray()
                            keys.split(",", "，").map { it.trim() }.filter { it.isNotBlank() }
                                .forEach { keysArray.put(it) }
                            onConfirm(
                                WorldEntryEntity(
                                    id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                                    worldbookId = initial?.worldbookId.orEmpty(),
                                    keysJson = keysArray.toString(),
                                    content = content.trim(),
                                    alwaysOn = alwaysOn,
                                    priority = priority.toIntOrNull() ?: 0,
                                )
                            )
                        }
                    },
                ) { Text("保存", color = GlassTokens.Primary) }
            }
        }
    }
}

@Composable
private fun WorldField(
    label: String,
    value: String,
    minLines: Int = 1,
    onChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(label, color = GlassTokens.TextSecondary, fontSize = 10.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            minLines = minLines,
            maxLines = if (minLines > 1) minLines + 4 else 1,
            singleLine = minLines == 1,
            shape = RoundedCornerShape(12.dp),
            colors = worldFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun worldFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color(0x14FFFFFF),
    unfocusedContainerColor = Color(0x0DFFFFFF),
    focusedTextColor = GlassTokens.TextPrimary,
    unfocusedTextColor = GlassTokens.TextPrimary,
    cursorColor = GlassTokens.Primary,
    focusedIndicatorColor = GlassTokens.Primary.copy(alpha = 0.6f),
    unfocusedIndicatorColor = Color(0x1FFFFFFF),
)

/** 导出：doc + entries 合成一个 JSON 文件文本 */
private fun buildExportJson(docJson: String, entries: List<WorldEntryEntity>): String {
    val root = runCatching { JSONObject(docJson) }.getOrElse { JSONObject() }
    val array = JSONArray()
    entries.forEach { entry ->
        array.put(
            JSONObject()
                .put("keys", runCatching { JSONArray(entry.keysJson) }.getOrElse { JSONArray() })
                .put("content", entry.content)
                .put("alwaysOn", entry.alwaysOn)
                .put("priority", entry.priority)
        )
    }
    root.put("entries", array)
    return root.toString()
}