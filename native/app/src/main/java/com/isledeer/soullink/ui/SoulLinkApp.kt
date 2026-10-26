package com.isledeer.soullink.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * 路由表。
 *
 * 目前只有两条：聊天（启动即进）与世界书（占位）。
 * 设置页等后续页往这里加，别在 Activity 里堆 if。
 */
object Routes {
    const val CHAT = "chat"
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
                onOpenWorldBook = { navController.navigate(Routes.WORLD_BOOK) }
            )
        }

        composable(Routes.WORLD_BOOK) {
            WorldBookScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}