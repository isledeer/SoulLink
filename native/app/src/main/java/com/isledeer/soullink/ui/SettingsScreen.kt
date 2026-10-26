package com.isledeer.soullink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.isledeer.soullink.data.ApiSettings

/**
 * API 配置页。
 *
 * 纯本地应用：Key 只写进本机 DataStore，不上传、不中转。
 * 这里只做最小可用集合（兼容任意 OpenAI 兼容端点），
 * 温度/最大 token 等高级项等「高级设置」折叠区再补。
 */
@Composable
fun SettingsScreen(
    state: ApiSettings,
    onChange: (ApiSettings) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var savedHint by remember { mutableStateOf(false) }

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
                .padding(horizontal = 10.dp, vertical = 8.dp)
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
                    text = "API 设置",
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
                    .padding(top = 16.dp)
            ) {
                SettingsField(
                    label = "API Base URL",
                    value = state.baseUrl,
                    placeholder = "https://api.openai.com/v1",
                    onChange = { onChange(state.copy(baseUrl = it)) },
                )
                SettingsField(
                    label = "API Key",
                    value = state.apiKey,
                    placeholder = "sk-…",
                    password = true,
                    onChange = { onChange(state.copy(apiKey = it)) },
                )
                SettingsField(
                    label = "模型名",
                    value = state.model,
                    placeholder = "gpt-4o-mini / deepseek-chat / …",
                    onChange = { onChange(state.copy(model = it)) },
                )
                SettingsField(
                    label = "最大输出 token",
                    value = state.maxTokens.toString(),
                    placeholder = "2048",
                    numeric = true,
                    onChange = { text ->
                        text.toIntOrNull()?.let { onChange(state.copy(maxTokens = it)) }
                    },
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        onSave()
                        savedHint = true
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassTokens.Primary,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("保存", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                if (savedHint) {
                    Text(
                        text = "已保存到本机",
                        color = GlassTokens.TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Text(
                    text = "Key 只保存在本设备（DataStore），不经过任何服务器。",
                    color = GlassTokens.TextSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    placeholder: String = "",
    password: Boolean = false,
    numeric: Boolean = false,
    onChange: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = label,
            color = GlassTokens.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = {
                Text(placeholder, color = GlassTokens.TextSecondary.copy(alpha = 0.45f), fontSize = 14.sp)
            },
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = if (numeric) {
                androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
            } else {
                androidx.compose.foundation.text.KeyboardOptions()
            },
            shape = RoundedCornerShape(16.dp),
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
    }
}