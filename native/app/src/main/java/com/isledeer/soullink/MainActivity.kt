package com.isledeer.soullink

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.isledeer.soullink.data.SettingsRepository
import com.isledeer.soullink.ui.AppTheme
import com.isledeer.soullink.ui.GlassTokens
import com.isledeer.soullink.ui.SoulLinkApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 纯 Compose 外壳入口。
 *
 * 职责：
 *   1. 按持久化的颜色模式应用色板（system / dark / light）
 *   2. 窗口底色跟随主题，消灭启动/重建时的白屏闪一下
 *   3. 完整 Material3 主题（色板 + 排版），组件不再各自硬编码颜色
 */
class MainActivity : ComponentActivity() {

    private var lightMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyPersistedTheme()
        setupSystemBars()

        setContent {
            val scheme = if (lightMode) {
                lightColorScheme(
                    primary = GlassTokens.Primary,
                    onPrimary = GlassTokens.OnPrimary,
                    secondary = GlassTokens.PrimaryDeep,
                    tertiary = GlassTokens.Accent,
                    background = GlassTokens.BackgroundBottom,
                    onBackground = GlassTokens.TextPrimary,
                    surface = GlassTokens.Surface,
                    onSurface = GlassTokens.TextPrimary,
                    surfaceVariant = GlassTokens.SurfaceElevated,
                    onSurfaceVariant = GlassTokens.TextSecondary,
                    outline = GlassTokens.GlassBorder,
                    outlineVariant = GlassTokens.Divider,
                )
            } else {
                darkColorScheme(
                    primary = GlassTokens.Primary,
                    onPrimary = GlassTokens.OnPrimary,
                    secondary = GlassTokens.PrimaryDeep,
                    tertiary = GlassTokens.Accent,
                    background = GlassTokens.BackgroundBottom,
                    onBackground = GlassTokens.TextPrimary,
                    surface = GlassTokens.Surface,
                    onSurface = GlassTokens.TextPrimary,
                    surfaceVariant = GlassTokens.SurfaceElevated,
                    onSurfaceVariant = GlassTokens.TextSecondary,
                    outline = GlassTokens.GlassBorder,
                    outlineVariant = GlassTokens.Divider,
                )
            }

            MaterialTheme(
                colorScheme = scheme,
                typography = SoulTypography,
            ) {
                SoulLinkApp()
            }
        }
    }

    private fun applyPersistedTheme() {
        val stored = runCatching {
            runBlocking { SettingsRepository(applicationContext).flow.first() }
        }.getOrNull()
        val mode = stored?.colorMode ?: "dark"
        val resolved = AppTheme.resolve(mode, this)
        AppTheme.apply(resolved)
        lightMode = resolved == "light"

        // 关键：窗口底色跟主题走，避免任何时刻露出系统默认白色
        window.setBackgroundDrawable(ColorDrawable(GlassTokens.BackgroundBottom.toArgb()))
    }

    private fun setupSystemBars() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = GlassTokens.BackgroundTop.toArgb()
        window.navigationBarColor = GlassTokens.BackgroundBottom.toArgb()

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightMode
            isAppearanceLightNavigationBars = lightMode
        }
    }

    companion object {
        /** 统一排版：字号 / 字重 / 字距，Material 组件默认继承 */
        val SoulTypography = Typography(
            displaySmall = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                letterSpacing = 0.sp,
            ),
            headlineMedium = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                letterSpacing = 0.2.sp,
            ),
            titleLarge = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                letterSpacing = 0.2.sp,
            ),
            titleMedium = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                letterSpacing = 0.3.sp,
            ),
            titleSmall = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = 0.2.sp,
            ),
            bodyLarge = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.2.sp,
            ),
            bodyMedium = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                letterSpacing = 0.2.sp,
            ),
            bodySmall = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.2.sp,
            ),
            labelLarge = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = 0.3.sp,
            ),
            labelMedium = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 0.3.sp,
            ),
            labelSmall = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                letterSpacing = 0.4.sp,
            ),
        )
    }
}