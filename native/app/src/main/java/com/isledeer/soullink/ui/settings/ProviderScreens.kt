package com.isledeer.soullink.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.isledeer.soullink.data.ProviderConfig
import com.isledeer.soullink.ui.GlassTokens
import com.isledeer.soullink.ui.chat.BrandIcon
import com.isledeer.soullink.ui.liquidGlass
import com.isledeer.soullink.ui.components.AuroraBackground
import kotlinx.coroutines.launch

@Composable
private fun headerBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(cornerRadius = GlassTokens.BarRadius)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("←", color = GlassTokens.TextSecondary, fontSize = 18.sp) }
        Text(title, color = GlassTokens.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
        actions()
    }
}

/* --------------------------- 提供商列表页 --------------------------- */

@Composable
fun ProviderListScreen(
    providers: List<ProviderConfig>,
    modelCounts: Map<String, Int>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSave: () -> Unit,
) {
    AuroraBackground(
        modifier = Modifier.fillMaxSize()
        ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            headerBar("提供商", onBack) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Add, contentDescription = "新建", tint = GlassTokens.Primary, modifier = Modifier.size(20.dp)) }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(providers, key = { it.id }) { provider ->
                    ProviderRow(
                        provider = provider,
                        modelCount = modelCounts[provider.id] ?: provider.modelList().size,
                        onOpen = { onOpen(provider.id) },
                        onDelete = { onDelete(provider.id) },
                    )
                }
            }

            Button(
                onClick = onSave,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GlassTokens.Primary,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("保存全部", fontSize = 15.sp) }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: ProviderConfig,
    modelCount: Int,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .liquidGlass(cornerRadius = 18.dp)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GlassTokens.Divider),
            contentAlignment = Alignment.Center,
        ) { BrandIcon(modelName = provider.name, size = 22.dp) }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(provider.name.ifBlank { "未命名" }, color = GlassTokens.TextPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(if (provider.enabled) "启用" else "停用", Color(0xFF16A34A))
                Tag("$modelCount 个模型", GlassTokens.Primary)
            }
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
                    text = { Text("删除", color = GlassTokens.Accent) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/* --------------------------- 提供商详情页 --------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDetailScreen(
    provider: ProviderConfig,
    fetchedModels: List<String>,
    testResult: String?,
    testing: Boolean,
    balanceText: String?,
    onQueryBalance: () -> Unit,
    onChange: (ProviderConfig) -> Unit,
    onTest: () -> Unit,
    fetchModels: suspend () -> Result<List<String>>,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var keyVisible by remember { mutableStateOf(false) }
    var fetchSheet by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var modelSettingsTarget by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AuroraBackground(
        modifier = Modifier.fillMaxSize()
        ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            headerBar(provider.name.ifBlank { "未命名" }, onBack) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onSave),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Check, contentDescription = "保存", tint = GlassTokens.Primary, modifier = Modifier.size(20.dp)) }
            }

            Spacer(Modifier.height(8.dp))

            TabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
                contentColor = GlassTokens.Primary,
            ) {
                listOf("配置", "模型", "高级设置").forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title, fontSize = 13.sp) },
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> ConfigTab(
                        provider = provider,
                        keyVisible = keyVisible,
                        onToggleKey = { keyVisible = !keyVisible },
                        onChange = onChange,
                        onTest = onTest,
                        testResult = testResult,
                        testing = testing,
                        balanceText = balanceText,
                        onQueryBalance = onQueryBalance,
                    )
                    1 -> ModelsTab(provider, fetchedModels, onChange, { fetchSheet = true }, { addDialog = true }, { modelSettingsTarget = it })
                    2 -> AdvancedTab(provider, onChange)
                }
            }
        }

        if (fetchSheet) {
            FetchModelsSheet(
                initialChecked = provider.modelList(),
                fetch = fetchModels,
                onConfirm = { list ->
                    val array = org.json.JSONArray()
                    list.forEach { array.put(it) }
                    onChange(provider.copy(enabledModelsJson = array.toString()))
                    fetchSheet = false
                },
                onDismiss = { fetchSheet = false },
            )
        }

        modelSettingsTarget?.let { target ->
            ModelSettingsSheet(
                provider = provider,
                model = target,
                onChange = onChange,
                onDismiss = { modelSettingsTarget = null },
            )
        }

        if (addDialog) {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { addDialog = false },
                containerColor = GlassTokens.SheetBg,
                title = { Text("添加新模型", color = GlassTokens.TextPrimary, fontSize = 15.sp) },
                text = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        placeholder = { Text("模型名称", color = GlassTokens.TextSecondary.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            val list = (provider.modelList() + trimmed).distinct()
                            val array = org.json.JSONArray()
                            list.forEach { array.put(it) }
                            onChange(provider.copy(enabledModelsJson = array.toString()))
                        }
                        addDialog = false
                    }) { Text("保存", color = GlassTokens.Primary) }
                },
                dismissButton = {
                    TextButton(onClick = { addDialog = false }) { Text("取消", color = GlassTokens.TextSecondary) }
                },
            )
        }
    }
}

@Composable
private fun ConfigTab(
    provider: ProviderConfig,
    keyVisible: Boolean,
    onToggleKey: () -> Unit,
    onChange: (ProviderConfig) -> Unit,
    onTest: () -> Unit,
    testResult: String?,
    testing: Boolean,
    balanceText: String?,
    onQueryBalance: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp)
    ) {
        CardField("启用") {
            Switch(checked = provider.enabled, onCheckedChange = { onChange(provider.copy(enabled = it)) })
        }
        TextFieldCard("名称", provider.name) { onChange(provider.copy(name = it)) }
        TextFieldCard("API Key", provider.apiKey, password = !keyVisible, trailing = {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onToggleKey)
                    .padding(6.dp),
            ) {
                Icon(
                    if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = "可见性",
                    tint = GlassTokens.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }) { onChange(provider.copy(apiKey = it)) }
        TextFieldCard("API Base Url", provider.baseUrl, leading = {
            Icon(Icons.Outlined.Sync, contentDescription = null, tint = GlassTokens.TextSecondary, modifier = Modifier.size(16.dp))
        }) { onChange(provider.copy(baseUrl = it)) }
        TextFieldCard("API 路径", provider.apiPath) { onChange(provider.copy(apiPath = it)) }
        TextFieldCard("余额查询 URL（可选）", provider.balanceUrl) { onChange(provider.copy(balanceUrl = it)) }
        TextFieldCard("余额 JSON 路径（如 data.balance）", provider.balanceJsonPath) { onChange(provider.copy(balanceJsonPath = it)) }
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = onQueryBalance,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GlassTokens.Primary.copy(alpha = 0.2f),
                contentColor = GlassTokens.Primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("查询余额", fontSize = 13.sp) }

        balanceText?.let {
            Text(it, color = GlassTokens.Primary, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onTest,
            enabled = !testing,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (testing) "测试中…" else "测试连通性", fontSize = 13.sp) }

        testResult?.let {
            Text(it, color = if (it.startsWith("连接成功")) GlassTokens.Primary else GlassTokens.Accent, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ModelsTab(
    provider: ProviderConfig,
    fetchedModels: List<String>,
    onChange: (ProviderConfig) -> Unit,
    onOpenFetch: () -> Unit,
    onOpenAdd: () -> Unit,
    onOpenModelSettings: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(provider.modelList(), key = { it }) { model ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .liquidGlass(cornerRadius = 16.dp)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrandIcon(modelName = model, size = 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(model, color = GlassTokens.TextPrimary, fontSize = 13.sp, maxLines = 1)
                        Text("聊天", color = GlassTokens.TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
                    }
                    Box {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable { onOpenModelSettings(model) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = GlassTokens.TextSecondary, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onOpenFetch,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.BubbleAi, contentColor = GlassTokens.TextPrimary),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("拉取模型", fontSize = 12.sp)
            }
            Button(
                onClick = onOpenAdd,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary, contentColor = Color.White),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加新模型", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AdvancedTab(
    provider: ProviderConfig,
    onChange: (ProviderConfig) -> Unit,
) {
    var headers by remember(provider.id) { mutableStateOf(mapToLinesText(jsonMap(provider.customHeaders))) }
    var bodies by remember(provider.id) { mutableStateOf(mapToLinesText(jsonMap(provider.customBodies))) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp)
    ) {
        Text("自定义 Headers（每行 key: value）", color = GlassTokens.TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = headers,
            onValueChange = { headers = it },
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text("自定义 Body（每行 key: value）", color = GlassTokens.TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = bodies,
            onValueChange = { bodies = it },
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = {
            onChange(provider.copy(customHeaders = linesTextToMap(headers), customBodies = linesTextToMap(bodies)))
        }) { Text("应用修改", color = GlassTokens.Primary) }
    }
}

/* --------------------------- 拉取模型 Sheet --------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FetchModelsSheet(
    initialChecked: List<String>,
    fetch: suspend () -> Result<List<String>>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var all by remember { mutableStateOf<List<String>?>(null) }
    var checked by remember { mutableStateOf(initialChecked.toMutableSet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val result = fetch()
        result.onSuccess { all = it }
            .onFailure { error = it.message ?: "拉取失败" }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden },
        ),
        containerColor = GlassTokens.SheetBg,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("可用模型", color = GlassTokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            val models = all
            when {
                error != null -> Text(error.orEmpty(), color = GlassTokens.Accent, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                models == null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = GlassTokens.Primary, modifier = Modifier.size(26.dp)) }
                models.isEmpty() -> Text("没有拉到模型，可点右上角添加", color = GlassTokens.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    val visible = if (query.isBlank()) models else models.filter { it.contains(query, ignoreCase = true) }
                    items(visible, key = { it }) { model ->
                        val isChecked = checked.contains(model)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    checked = if (isChecked) (checked - model).toMutableSet() else (checked + model).toMutableSet()
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(model, color = GlassTokens.TextPrimary, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = if (isChecked) Icons.Outlined.Check else Icons.Outlined.Add,
                                contentDescription = null,
                                tint = if (isChecked) GlassTokens.Primary else GlassTokens.TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = {
                    Text("搜索模型名", color = GlassTokens.TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp)
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = GlassTokens.FieldBgFocused,
                    unfocusedContainerColor = GlassTokens.FieldBg,
                    focusedTextColor = GlassTokens.TextPrimary,
                    unfocusedTextColor = GlassTokens.TextPrimary,
                    cursorColor = GlassTokens.Primary,
                    focusedIndicatorColor = GlassTokens.Primary.copy(alpha = 0.6f),
                    unfocusedIndicatorColor = Color(0x1FFFFFFF),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onConfirm(checked.toList()) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存勾选", fontSize = 13.sp) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* --------------------------- 工具函数 --------------------------- */

@Composable
private fun CardField(title: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .liquidGlass(cornerRadius = 16.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = GlassTokens.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        content()
    }
}

@Composable
private fun TextFieldCard(
    title: String,
    value: String,
    password: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .liquidGlass(cornerRadius = 16.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(title, color = GlassTokens.TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(8.dp))
            }
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = GlassTokens.FieldBgFocused,
                    unfocusedContainerColor = GlassTokens.FieldBg,
                    focusedTextColor = GlassTokens.TextPrimary,
                    unfocusedTextColor = GlassTokens.TextPrimary,
                    cursorColor = GlassTokens.Primary,
                    focusedIndicatorColor = GlassTokens.Primary.copy(alpha = 0.6f),
                    unfocusedIndicatorColor = Color(0x1FFFFFFF),
                ),
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

private fun mapToLinesText(map: Map<String, String>): String =
    map.entries.joinToString("\n") { "${it.key}: ${it.value}" }

private fun linesTextToMap(text: String): String {
    val json = org.json.JSONObject()
    text.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEach
        val index = trimmed.indexOf(':')
        if (index <= 0) return@forEach
        json.put(trimmed.substring(0, index).trim(), trimmed.substring(index + 1).trim())
    }
    return json.toString()
}

private fun jsonMap(json: String): Map<String, String> = runCatching {
    val obj = org.json.JSONObject(json)
    obj.keys().asSequence().associateWith { key -> obj.optString(key) }
}.getOrElse { emptyMap() }


/* ------------------------- 模型更多设置 Sheet ------------------------- */

private fun modelSettings(provider: ProviderConfig, model: String): org.json.JSONObject {
    val root = runCatching { org.json.JSONObject(provider.modelSettingsJson) }.getOrNull()
        ?: org.json.JSONObject()
    return root.optJSONObject(model) ?: org.json.JSONObject()
}

private fun withModelSettings(
    provider: ProviderConfig,
    model: String,
    obj: org.json.JSONObject,
): ProviderConfig {
    val root = runCatching { org.json.JSONObject(provider.modelSettingsJson) }.getOrNull()
        ?: org.json.JSONObject()
    root.put(model, obj)
    return provider.copy(modelSettingsJson = root.toString())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSettingsSheet(
    provider: ProviderConfig,
    model: String,
    onChange: (ProviderConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember(model, provider.modelSettingsJson) { modelSettings(provider, model) }
    var alias by remember(model, provider.modelSettingsJson) { mutableStateOf(initial.optString("alias", "")) }
    var temperature by remember(model, provider.modelSettingsJson) { mutableFloatStateOf(initial.optDouble("temperature", 0.8).toFloat()) }
    var context by remember(model, provider.modelSettingsJson) { mutableStateOf(initial.optInt("context", 0).toString()) }
    var thinking by remember(model, provider.modelSettingsJson) { mutableStateOf(initial.optBoolean("thinking", false)) }
    var vision by remember(model, provider.modelSettingsJson) { mutableStateOf(initial.optBoolean("vision", false)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden },
        ),
        containerColor = GlassTokens.SheetBg,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(model, color = GlassTokens.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            Text("别名", color = GlassTokens.TextSecondary, fontSize = 11.sp)
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = {
                    Text("显示名称（留空用原名）", color = GlassTokens.TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp)
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = GlassTokens.FieldBgFocused,
                    unfocusedContainerColor = GlassTokens.FieldBg,
                    focusedTextColor = GlassTokens.TextPrimary,
                    unfocusedTextColor = GlassTokens.TextPrimary,
                    cursorColor = GlassTokens.Primary,
                    focusedIndicatorColor = GlassTokens.Primary.copy(alpha = 0.6f),
                    unfocusedIndicatorColor = Color(0x1FFFFFFF),
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text("温度 ${((temperature * 100).toInt() / 100.0)}", color = GlassTokens.TextSecondary, fontSize = 11.sp)
            Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0f..2f)

            Spacer(Modifier.height(8.dp))
            Text("上下文长度（0 = 使用默认）", color = GlassTokens.TextSecondary, fontSize = 11.sp)
            OutlinedTextField(
                value = context,
                onValueChange = { context = it.filter { ch -> ch.isDigit() } },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = GlassTokens.FieldBgFocused,
                    unfocusedContainerColor = GlassTokens.FieldBg,
                    focusedTextColor = GlassTokens.TextPrimary,
                    unfocusedTextColor = GlassTokens.TextPrimary,
                    cursorColor = GlassTokens.Primary,
                    focusedIndicatorColor = GlassTokens.Primary.copy(alpha = 0.6f),
                    unfocusedIndicatorColor = Color(0x1FFFFFFF),
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("思考能力", color = GlassTokens.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = thinking, onCheckedChange = { thinking = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("视觉能力", color = GlassTokens.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = vision, onCheckedChange = { vision = it })
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val obj = org.json.JSONObject()
                        .put("alias", alias.trim())
                        .put("temperature", temperature.toDouble())
                        .put("context", context.toIntOrNull() ?: 0)
                        .put("thinking", thinking)
                        .put("vision", vision)
                    onChange(withModelSettings(provider, model, obj))
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlassTokens.Primary, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存设置", fontSize = 13.sp) }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    val list = provider.modelList() - model
                    val array = org.json.JSONArray()
                    list.forEach { array.put(it) }
                    onChange(provider.copy(enabledModelsJson = array.toString()))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("移除模型", color = GlassTokens.Accent, fontSize = 13.sp) }

            Spacer(Modifier.height(24.dp))
        }
    }
}
