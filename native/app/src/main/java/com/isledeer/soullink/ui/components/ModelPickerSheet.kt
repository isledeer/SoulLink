package com.isledeer.soullink.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isledeer.soullink.data.ProviderConfig
import com.isledeer.soullink.ui.GlassTokens
import com.isledeer.soullink.ui.components.Emphasized
import com.isledeer.soullink.ui.components.EmphasizedText
import com.isledeer.soullink.ui.liquidGlass
import kotlinx.coroutines.launch

/**
 * 两步模型选择器：先选运营商（仅用户已配置的 Provider），
 * 再从该运营商的 /v1/models 拉取模型列表供选择。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    providers: List<ProviderConfig>,
    fetchModels: suspend (ProviderConfig) -> Result<List<String>>,
    onPick: (model: String) -> Unit,
    onProviderPicked: (ProviderConfig) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var selectedProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var models by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xF2070B14),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            EmphasizedText(
                text = if (selectedProvider == null) "选择运营商" else "选择模型",
                style = Emphasized.titleSmall,
                color = GlassTokens.TextSecondary,
            )
            Spacer(Modifier.height(8.dp))

            val provider = selectedProvider
            if (provider == null) {
                providers.forEach { candidate ->
                    SheetRow(candidate.name.ifBlank { "未命名" }) {
                        onProviderPicked(candidate)
                        selectedProvider = candidate
                        models = null
                        error = null
                        scope.launch {
                            val result = fetchModels(candidate)
                            result.onSuccess { models = it }
                                .onFailure { error = it.message ?: "拉取失败" }
                        }
                    }
                }
                if (providers.isEmpty()) {
                    Text(
                        text = "还没有配置运营商，先去设置里添加。",
                        color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                val list = models
                when {
                    error != null -> Text(
                        text = error.orEmpty(),
                        color = GlassTokens.Accent,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )

                    list == null -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = GlassTokens.Primary, modifier = Modifier.size(28.dp)) }

                    list.isEmpty() -> Text(
                        text = "该运营商没有返回模型",
                        color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )

                    else -> list.forEach { model ->
                        SheetRow(model) { onPick(model) }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "← 返回选择运营商（${provider.name.ifBlank { "未命名" }}）",
                    color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedProvider = null; models = null; error = null }
                        .padding(vertical = 10.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = GlassTokens.TextPrimary,
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}