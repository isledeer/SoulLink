package com.isledeer.soullink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.isledeer.soullink.ui.assistant.AssistantDetailScreen
import com.isledeer.soullink.ui.assistant.AssistantListScreen
import com.isledeer.soullink.ui.assistant.AssistantViewModel
import com.isledeer.soullink.ui.settings.SearchServiceScreen
import com.isledeer.soullink.ui.settings.SettingsOverviewScreen
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val WORLD_BOOK = "worldbook"
    const val ASSISTANTS = "assistants"
    const val ASSISTANT_DETAIL = "assistant/{id}"
    const val PROVIDERS = "providers"
    const val SEARCH_SERVICE = "search_service"

    fun assistantDetail(id: String) = "assistant/$id"
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
                onOpenAssistants = { navController.navigate(Routes.ASSISTANTS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsOverviewScreen(
                onBack = { navController.popBackStack() },
                onOpenAssistants = { navController.navigate(Routes.ASSISTANTS) },
                onOpenProviders = { navController.navigate(Routes.PROVIDERS) },
                onOpenSearchService = { navController.navigate(Routes.SEARCH_SERVICE) },
            )
        }

        composable(Routes.PROVIDERS) {
            SettingsRoute(onBack = { navController.popBackStack() })
        }

        composable(Routes.SEARCH_SERVICE) {
            SearchServiceRoute(onBack = { navController.popBackStack() })
        }

        composable(Routes.WORLD_BOOK) {
            WorldBookScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ASSISTANTS) {
            AssistantListRoute(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(Routes.assistantDetail(id)) },
            )
        }

        composable(
            route = Routes.ASSISTANT_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            AssistantDetailRoute(
                assistantId = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun AssistantListRoute(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val viewModel: AssistantViewModel = koinViewModel()
    val assistants by viewModel.assistants.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    AssistantListScreen(
        assistants = assistants,
        query = query,
        onQueryChange = viewModel::search,
        onBack = onBack,
        onCreate = { onOpen(viewModel.create()) },
        onOpen = onOpen,
        onClone = viewModel::clone,
        onDelete = viewModel::delete,
    )
}

@Composable
private fun AssistantDetailRoute(
    assistantId: String,
    onBack: () -> Unit,
) {
    val viewModel: AssistantViewModel = koinViewModel()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val appSettings by viewModel.settings.collectAsStateWithLifecycle()

    // 新建助手走 create() 后立刻导航：upsert 与 findById 可能并发，容错重试几次
    LaunchedEffect(assistantId) {
        repeat(4) {
            viewModel.load(assistantId)
            if (viewModel.detail.value != null) return@LaunchedEffect
            delay(150)
        }
    }

    val current = detail ?: return

    AssistantDetailScreen(
        assistant = current,
        providers = appSettings.providers,
        onBack = onBack,
        onSave = { updated ->
            viewModel.update { updated }
            viewModel.save { onBack() }
        },
    )
}

@Composable
private fun SearchServiceRoute(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    SearchServiceScreen(
        keys = state.searchKeys,
        onChangeKey = viewModel::setSearchKey,
        onSave = viewModel::save,
        onBack = onBack,
    )
}
