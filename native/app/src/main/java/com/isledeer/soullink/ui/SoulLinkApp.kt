package com.isledeer.soullink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.androidx.compose.koinViewModel

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
                onOpenProviderSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenWorldBook = { navController.navigate(Routes.WORLD_BOOK) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsRoute(onBack = { navController.popBackStack() })
        }

        composable(Routes.WORLD_BOOK) {
            WorldBookScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun SettingsRoute(onBack: () -> Unit) {
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
        onSave = viewModel::save,
        onBack = onBack,
    )
}