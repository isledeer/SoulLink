package com.isledeer.soullink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isledeer.soullink.data.AppSettings
import com.isledeer.soullink.data.Feature
import com.isledeer.soullink.data.ProviderConfig

/**
 * 设置页：Provider 管理 + 功能模型绑定。
 * 大圆角卡片堆叠，视觉与聊天页同源（GlassTokens + liquidGlass）。
 */
@Composable
fun SettingsScreen(
    state: AppSettings,
    testingProviderId: String?,
    testResult: String?,
    onAddProvider: () -> Unit,
    onUpdateProvider: (ProviderConfig) -> Unit,
    onRemoveProvider: (String) -> Unit,
    onSetBinding: (Feature, String, String) -> Unit,
    onTest: (ProviderConfig) -> Unit,
    fetchModels: suspend (ProviderConfig) -> Result<List<String>>,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var pickerFeature by remember { mutableStateOf<Feature?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(GlassTokens.BackgroundTop, GlassTokens.BackgroundBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                ) {
                    Text("←", color = GlassTokens.TextSecondary, fontSize = 18.sp)
                }
                Text(
                    text = "设置",
                    color = GlassTokens.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 14.dp)
            ) {
                SectionTitle("提供商")

                state.providers.forEach { provider ->
                    ProviderCard(
                        provider = provider,
                        testing = testingProviderId == provider.id,
                        onChange = onUpdateProvider,
                        onRemove = { onRemoveProvider(provider.id) },
                        onTest = { onTest(provider) },
                    )
                }

                if (state.providers.isEmpty()) {
                    Text(
                        text = "还没有提供商，先添加一个（任何 OpenAI 兼容端点都行）。",
                        color = GlassTokens.TextSecondary.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassTokens.Primary.copy(alpha = 0.16f))
                        .clickable(onClick = onAddProvider)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("＋ 添加提供商", color = GlassTokens.Primary, fontSize = 13.sp)
                }

                testResult?.let { result ->
                    Text(
                        text = result,
                        color = if (result.startsWith("连接成功")) GlassTokens.Primary else GlassTokens.Accent,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionTitle("功能模型绑定")

                Feature.entries.forEach { feature ->
                    BindingCard(
                        feature = feature,
                        settings = state,
                        onSetBinding = onSetBinding,
                        onOpenPicker = { pickerFeature = feature },
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onSave,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassTokens.Primary,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("保存全部", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Text(
                    text = "所有 Key 只保存在本机 DataStore，不经过任何服务器。",
                    color = GlassTokens.TextSecondary.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 28.dp),
                )
            }
        }

        pickerFeature?.let { feature ->
            val currentBinding = state.bindings[feature]
            com.isledeer.soullink.ui.components.ModelPickerSheet(
                providers = state.providers,
                fetchModels = fetchModels,
                onProviderPicked = { provider ->
                    onSetBinding(feature, provider.id, currentBinding?.model.orEmpty())
                },
                onPick = { model ->
                    val providerId = state.bindings[feature]?.providerId
                        ?: state.providers.firstOrNull()?.id.orEmpty()
                    onSetBinding(feature, providerId, model)
                    pickerFeature = null
                },
                onDismiss = { pickerFeature = null },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = GlassTokens.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    testing: Boolean,
    onChange: (ProviderConfig) -> Unit,
    onRemove: () -> Unit,
    onTest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .liquidGlass(cornerRadius = 20.dp)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsInput(
                value = provider.name,
                placeholder = "名称（如 DeepSeek）",
                onValueChange = { onChange(provider.copy(name = it)) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "删除",
                color = GlassTokens.Accent,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        SettingsInput(
            value = provider.baseUrl,
            placeholder = "Base URL（https://api.xxx.com/v1）",
            onValueChange = { onChange(provider.copy(baseUrl = it)) },
        )

        Spacer(Modifier.height(8.dp))
        SettingsInput(
            value = provider.apiKey,
            placeholder = "API Key（sk-…）",
            password = true,
            onValueChange = { onChange(provider.copy(apiKey = it)) },
        )

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (testing) "测试中…" else "测试连通性",
                color = GlassTokens.Primary,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !testing, onClick = onTest)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun BindingCard(
    feature: Feature,
    settings: AppSettings,
    onSetBinding: (Feature, String, String) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val binding = settings.bindings[feature]
    val providerId = binding?.providerId
        ?: settings.bindings[Feature.CHAT]?.providerId
        ?: settings.providers.firstOrNull()?.id
        .orEmpty()
    val model = binding?.model.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .liquidGlass(cornerRadius = 20.dp)
            .padding(14.dp)
    ) {
        Text(feature.label, color = GlassTokens.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            settings.providers.forEach { provider ->
                val active = provider.id == providerId
                Text(
                    text = provider.name.ifBlank { "未命名" },
                    color = if (active) Color.White else GlassTokens.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (active) GlassTokens.Primary.copy(alpha = 0.85f)
                            else GlassTokens.BubbleAi.copy(alpha = 0.7f)
                        )
                        .clickable { onSetBinding(feature, provider.id, model) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x0DFFFFFF))
                .clickable(onClick = onOpenPicker)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = model.ifBlank { "点击选择模型" },
                color = if (model.isBlank()) GlassTokens.TextSecondary.copy(alpha = 0.7f) else GlassTokens.TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("›", color = GlassTokens.TextSecondary, fontSize = 15.sp)
        }
    }
}

@Composable
private fun SettingsInput(
    value: String,
    placeholder: String,
    password: Boolean = false,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = {
            Text(
                text = placeholder,
                color = GlassTokens.TextSecondary.copy(alpha = 0.45f),
                fontSize = 12.sp,
            )
        },
        visualTransformation = if (password) {
            PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0x14FFFFFF),
            unfocusedContainerColor = Color(0x0DFFFFFF),
            focusedTextColor = GlassTokens.TextPrimary,
            unfocusedTextColor = GlassTokens.TextPrimary,
            cursorColor = GlassTokens.Primary,
            focusedIndicatorColor = GlassTokens.Primary.copy(alpha = 0.6f),
            unfocusedIndicatorColor = Color(0x1FFFFFFF),
        ),
        modifier = modifier,
    )
}


/**
 * 设置页接线层：Koin 取 ViewModel，状态透传给纯 UI 的 SettingsScreen。
 * 放在这里而不是 SoulLinkApp 里，让路由文件只做导航。
 */
@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val testingId by viewModel.testing.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        testingProviderId = testingId,
        testResult = testResult,
        onAddProvider = viewModel::addProvider,
        onUpdateProvider = viewModel::updateProvider,
        onRemoveProvider = viewModel::removeProvider,
        onSetBinding = { feature, providerId, model ->
            viewModel.setBinding(feature, providerId, model)
        },
        onTest = viewModel::test,
        fetchModels = viewModel::fetchModels,
        onSave = viewModel::save,
        onBack = onBack,
    )
}
