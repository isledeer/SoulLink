package com.isledeer.soullink.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isledeer.soullink.data.ProviderConfig
import com.isledeer.soullink.ui.GlassTokens

/**
 * 本地模型选择器：不再发网络请求。
 * 按 Provider 分组展示其 enabledModels（设置页勾选过的），
 * 没有启用任何模型的 Provider 不渲染。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    providers: List<ProviderConfig>,
    onPick: (ProviderConfig, String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden },
        ),
        containerColor = GlassTokens.SheetBg,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("选择模型", color = GlassTokens.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))

            val grouped = providers
                .filter { it.modelList().isNotEmpty() }
                .groupBy { it }

            if (grouped.isEmpty()) {
                Text(
                    text = "还没有启用的模型：去设置 → 提供商 → 拉取模型并勾选。",
                    color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp),
                ) {
                    grouped.forEach { (provider, _) ->
                        item(key = "h-${provider.id}") {
                            Text(
                                text = provider.name.ifBlank { "未命名" },
                                color = GlassTokens.TextSecondary.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp),
                            )
                        }
                        items(provider.modelList(), key = { "${provider.id}-$it" }) { model ->
                            Text(
                                text = model,
                                color = GlassTokens.TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(GlassTokens.ControlRadius))
                                    .clickable { onPick(provider, model) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}