package com.isledeer.soullink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.androidx.compose.koinViewModel

/**
 * 路由表。启动即进聊天；设置与世界书都是二级页。
 */
object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val WORLD_BOOK = "worldbook"
}

@Composable
fun SoulLinkApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.CHAT,
    ) {
        composable(Routes.CHAT) {
            ChatRoute(
                onOpenWorldBook = { navController.navigate(Routes.WORLD_BOOK) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.WORLD_BOOK) {
            WorldBookScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun SettingsRoute(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        onChange = { updated -> viewModel.update { updated } },
        onSave = { viewModel.save() },
        onBack = onBack,
    )
}