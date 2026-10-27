package com.isledeer.soullink.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.isledeer.soullink.data.parseMcpServers
import com.isledeer.soullink.ui.assistant.AssistantDetailScreen
import com.isledeer.soullink.ui.assistant.AssistantListScreen
import com.isledeer.soullink.ui.assistant.AssistantViewModel
import com.isledeer.soullink.ui.settings.AboutScreen
import com.isledeer.soullink.ui.settings.BackupScreen
import com.isledeer.soullink.ui.settings.ExtensionsScreen
import com.isledeer.soullink.ui.settings.FeatureBindingsScreen
import com.isledeer.soullink.ui.settings.HelpScreen
import com.isledeer.soullink.ui.settings.McpServersScreen
import com.isledeer.soullink.ui.settings.MediaScreen
import com.isledeer.soullink.ui.settings.PreferencesScreen
import com.isledeer.soullink.ui.settings.ProviderDetailScreen
import com.isledeer.soullink.ui.settings.ProviderListScreen
import com.isledeer.soullink.ui.settings.RequestLogScreen
import com.isledeer.soullink.ui.settings.SearchServiceScreen
import com.isledeer.soullink.ui.settings.SettingsOverviewScreen
import com.isledeer.soullink.ui.settings.ShareScreen
import com.isledeer.soullink.ui.settings.SponsorScreen
import com.isledeer.soullink.ui.settings.StorageScreen
import com.isledeer.soullink.ui.settings.VoiceScreen
import com.isledeer.soullink.ui.settings.WebServerScreen
import com.isledeer.soullink.ui.worldbook.WorldBookEditScreen
import com.isledeer.soullink.ui.worldbook.WorldBookListScreen
import com.isledeer.soullink.ui.worldbook.WorldbookViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val WORLD_BOOK = "worldbooks"
    const val WORLD_BOOK_EDIT = "worldbook/{id}"

    fun worldbookEdit(id: String) = "worldbook/$id"
    const val ASSISTANTS = "assistants"
    const val ASSISTANT_DETAIL = "assistant/{id}"
    const val PROVIDERS = "providers"
    const val SEARCH_SERVICE = "search_service"
    const val PROVIDER_DETAIL = "provider/{id}"
    const val PREFERENCES = "settings_preferences"
    const val EXTENSIONS = "settings_extensions"
    const val FEATURES = "settings_features"
    const val VOICE = "settings_voice"
    const val MEDIA = "settings_media"
    const val MCP = "settings_mcp"
    const val WEB_SERVER = "settings_webserver"
    const val BACKUP = "settings_backup"
    const val STORAGE = "settings_storage"
    const val ABOUT = "settings_about"
    const val HELP = "settings_help"
    const val LOGS = "settings_logs"
    const val SPONSOR = "settings_sponsor"
    const val SHARE = "settings_share"

    fun providerDetail(id: String) = "provider/$id"

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
            SettingsOverviewRoute(
                onBack = { navController.popBackStack() },
                onOpen = { route -> navController.navigate(route) },
            )
        }

        composable(Routes.PREFERENCES) {
            PreferencesRoute(onBack = { navController.popBackStack() })
        }

        composable(Routes.EXTENSIONS) {
            ExtensionsScreen(
                onOpenFeatures = { navController.navigate(Routes.FEATURES) },
                onOpenAssistants = { navController.navigate(Routes.ASSISTANTS) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.FEATURES) {
            FeaturesRoute(onBack = { navController.popBackStack() })
        }

        composable(Routes.VOICE) {
            VoiceRoute(onBack = { navController.popBackStack() })
        }

        composable(Routes.MEDIA) {
            MediaRoute(onBack = { navController.popBackStack() })
        }

        composable(Routes.MCP) {
            McpRoute(onBack = { navController.popBackStack() })
        }

        composable(Routes.WEB_SERVER) {
            WebServerScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.BACKUP) {
            BackupScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.STORAGE) {
            StorageScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.HELP) {
            HelpScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.LOGS) {
            RequestLogScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SPONSOR) {
            SponsorScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SHARE) {
            ShareScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PROVIDERS) {
            ProviderListRoute(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(Routes.providerDetail(id)) },
            )
        }

        composable(
            route = Routes.PROVIDER_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            ProviderDetailRoute(
                providerId = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SEARCH_SERVICE) {
            SearchServiceRoute(onBack = { navController.popBackStack() })
        }

        composable(Routes.WORLD_BOOK) {
            WorldbookListRoute(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(Routes.worldbookEdit(id)) },
                onBackToChat = { navController.popBackStack(Routes.CHAT, false) },
            )
        }

        composable(
            route = Routes.WORLD_BOOK_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            WorldbookEditRoute(
                bookId = entry.arguments?.getString("id").orEmpty(),
                onBack = { navController.popBackStack() },
                onBackToChat = { navController.popBackStack(Routes.CHAT, false) },
            )
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

@Composable
private fun SettingsOverviewRoute(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }

    SettingsOverviewScreen(
        colorMode = state.colorMode,
        onSetColorMode = { mode ->
            viewModel.setColorMode(mode)
            AppTheme.apply(AppTheme.resolve(mode, context))
            (context as? ComponentActivity)?.recreate()
        },
        onBack = onBack,
        onOpenAssistants = { onOpen(Routes.ASSISTANTS) },
        onOpenProviders = { onOpen(Routes.PROVIDERS) },
        onOpenSearchService = { onOpen(Routes.SEARCH_SERVICE) },
        onOpenPreferences = { onOpen(Routes.PREFERENCES) },
        onOpenExtensions = { onOpen(Routes.EXTENSIONS) },
        onOpenFeatures = { onOpen(Routes.FEATURES) },
        onOpenVoice = { onOpen(Routes.VOICE) },
        onOpenMedia = { onOpen(Routes.MEDIA) },
        onOpenMcp = { onOpen(Routes.MCP) },
        onOpenWebServer = { onOpen(Routes.WEB_SERVER) },
        onOpenBackup = { onOpen(Routes.BACKUP) },
        onOpenStorage = { onOpen(Routes.STORAGE) },
        onOpenAbout = { onOpen(Routes.ABOUT) },
        onOpenHelp = { onOpen(Routes.HELP) },
        onOpenLogs = { onOpen(Routes.LOGS) },
        onOpenSponsor = { onOpen(Routes.SPONSOR) },
        onOpenShare = { onOpen(Routes.SHARE) },
    )
}

@Composable
private fun PreferencesRoute(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember { mutableStateOf(notificationsGranted(context)) }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result -> granted = result || notificationsGranted(context) }

    PreferencesScreen(
        settings = state,
        notificationsGranted = granted,
        onSetColorMode = { mode -> viewModel.setColorMode(mode) },
        onSetTts = { rate, pitch -> viewModel.setTts(rate, pitch) },
        onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                granted = true
            }
        },
        onSave = viewModel::save,
        onBack = onBack,
    )
}

private fun notificationsGranted(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun FeaturesRoute(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    FeatureBindingsScreen(
        settings = state,
        onChangePrompt = viewModel::setDefaultPrompt,
        onSetBinding = viewModel::setBinding,
        onBack = onBack,
    )
}

@Composable
private fun VoiceRoute(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    VoiceScreen(
        settings = state,
        onSetTts = viewModel::setTts,
        onSave = viewModel::save,
        onBack = onBack,
    )
}

@Composable
private fun MediaRoute(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    MediaScreen(
        settings = state,
        onSetBinding = viewModel::setBinding,
        onBack = onBack,
    )
}

@Composable
private fun McpRoute(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    McpServersScreen(
        servers = parseMcpServers(state.mcpServersJson),
        onAdd = viewModel::addMcpServer,
        onUpdate = viewModel::updateMcpServer,
        onRemove = viewModel::removeMcpServer,
        onSave = viewModel::save,
        onBack = onBack,
    )
}

@Composable
private fun ProviderListRoute(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fetchedModels by viewModel.fetchedModels.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    ProviderListScreen(
        providers = state.providers,
        modelCounts = fetchedModels.mapValues { it.value.size },
        onBack = onBack,
        onAdd = { onOpen(viewModel.addProvider()) },
        onOpen = onOpen,
        onDelete = viewModel::removeProvider,
        onSave = viewModel::save,
    )
}

@Composable
private fun ProviderDetailRoute(
    providerId: String,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fetchedModels by viewModel.fetchedModels.collectAsStateWithLifecycle()
    val testingId by viewModel.testing.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val balanceText by viewModel.balanceText.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    val provider = state.providers.firstOrNull { it.id == providerId }
    if (provider == null) {
        // 防呆：详情对象缺失时给兜底页，绝不白屏
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(GlassTokens.BackgroundTop, GlassTokens.BackgroundBottom)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("未找到该提供商", color = GlassTokens.TextPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBack) { Text("返回", color = GlassTokens.Primary) }
            }
        }
        return
    }

    ProviderDetailScreen(
        provider = provider,
        fetchedModels = fetchedModels[providerId].orEmpty(),
        testResult = if (testingId == providerId) testResult else null,
        testing = testingId == providerId,
        balanceText = balanceText,
        onQueryBalance = { viewModel.queryBalance(provider) },
        onChange = viewModel::updateProvider,
        onTest = { viewModel.test(provider) },
        fetchModels = { viewModel.fetchModels(provider) },
        onSave = viewModel::save,
        onBack = onBack,
    )
}

@Composable
private fun WorldbookListRoute(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onBackToChat: () -> Unit,
) {
    val viewModel: WorldbookViewModel = koinViewModel()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.refresh() }

    WorldBookListScreen(
        books = books,
        onBack = onBack,
        onCreate = { template, title -> onOpen(viewModel.create(template, title)) },
        onOpen = onOpen,
        onDelete = viewModel::delete,
        onStart = { id ->
            scope.launch {
                val conversationId = viewModel.startSession(id)
                if (conversationId != null) {
                    ChatSelectionBridge.pendingConversationId = conversationId
                    onBackToChat()
                }
            }
        },
        onImport = { raw -> viewModel.importBook(raw) { } },
    )
}

@Composable
private fun WorldbookEditRoute(
    bookId: String,
    onBack: () -> Unit,
    onBackToChat: () -> Unit,
) {
    val viewModel: WorldbookViewModel = koinViewModel()
    val book by viewModel.detail.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val notice by viewModel.message.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(bookId) { viewModel.load(bookId) }

    val current = book
    if (current == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(GlassTokens.BackgroundTop, GlassTokens.BackgroundBottom)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("加载中…", color = GlassTokens.TextPrimary, fontSize = 14.sp)
        }
        return
    }

    WorldBookEditScreen(
        book = current,
        entries = entries,
        busy = busy,
        onBack = onBack,
        onSave = viewModel::save,
        onAiFill = { premise -> viewModel.aiFill(bookId, premise) { } },
        onStart = {
            scope.launch {
                val conversationId = viewModel.startSession(bookId)
                if (conversationId != null) {
                    ChatSelectionBridge.pendingConversationId = conversationId
                    onBackToChat()
                }
            }
        },
        notice = notice,
    )
}
