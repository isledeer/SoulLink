package com.isledeer.soullink

import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
 * v3 架构：聊天、设置、世界书全部由 Compose 绘制；
 * 全屏 WebView（LocalAssetServer + Http 内核）已废弃，
 * 相关类（LocalAssetServer / WebAppInterface / NativeSSE）暂时保留在工程里，
 * 将来只供 WorldBookScreen 挂世界书面板时按需复用。
 *
 * 这里只做三件事：
 *   1. 按持久化的颜色模式应用色板（system / dark / light）
 *   2. 刷状态栏 / 导航栏颜色（与 Compose 背景同色，避免顶部一条色差）
 *   3. 套一层 MaterialTheme（色板来自 GlassTokens，和聊天页一致）
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyPersistedTheme()
        setupSystemBars()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = GlassTokens.Primary,
                    secondary = GlassTokens.PrimaryDeep,
                    tertiary = GlassTokens.Accent,
                    background = GlassTokens.BackgroundBottom,
                    surface = GlassTokens.BackgroundBottom,
                    onPrimary = Color.White,
                    onSecondary = Color.White,
                    onBackground = GlassTokens.TextPrimary,
                    onSurface = GlassTokens.TextPrimary,
                )
            ) {
                SoulLinkApp()
            }
        }
    }

    /** 读取持久化颜色模式并切换色板；system 跟随系统深浅色设置。 */
    private fun applyPersistedTheme() {
        val stored = runCatching {
            runBlocking { SettingsRepository(applicationContext).flow.first() }
        }.getOrNull()
        val mode = stored?.colorMode ?: "dark"
        val resolved = when (mode) {
            "light" -> "light"
            "system" -> {
                val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
                if (night) "dark" else "light"
            }
            else -> "dark"
        }
        AppTheme.apply(resolved)
    }

    /**
     * 状态栏用聊天页渐变的顶部色，导航栏用底色 —— 上下都不出现色差条。
     * 暂不做真 edge-to-edge（内容钻到状态栏下），等 UI 稳定后再一起改。
     */
    private fun setupSystemBars() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = GlassTokens.BackgroundTop.toArgb()
        window.navigationBarColor = GlassTokens.BackgroundBottom.toArgb()

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}
