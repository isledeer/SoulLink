package com.isledeer.soullink.di

import com.isledeer.soullink.data.AppDatabase
import com.isledeer.soullink.data.GameLedgerRepository
import com.isledeer.soullink.data.MessageTreeRepository
import com.isledeer.soullink.data.SettingsRepository
import com.isledeer.soullink.net.SearchService
import com.isledeer.soullink.net.SseClient
import com.isledeer.soullink.tools.LocalTools
import com.isledeer.soullink.ui.ChatViewModel
import com.isledeer.soullink.ui.SettingsViewModel
import com.isledeer.soullink.ui.assistant.AssistantViewModel
import com.isledeer.soullink.util.Speaker
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // 存储
    single { AppDatabase.get(androidContext()) }
    single { get<AppDatabase>().assistantDao() }
    single { get<AppDatabase>().conversationDao() }
    single { get<AppDatabase>().messageTreeDao() }
    single { get<AppDatabase>().gameSessionDao() }
    single { get<AppDatabase>().gameEventDao() }
    single { get<AppDatabase>().worldBookDao() }
    single { MessageTreeRepository(get()) }
    single { GameLedgerRepository(get(), get(), get(), get()) }
    single { SettingsRepository(androidContext()) }

    // 设备能力 & 工具
    single { Speaker(androidContext()) }
    single { LocalTools(androidContext()) }

    // 网络
    single { SseClient() }
    single { SearchService() }
    single { com.isledeer.soullink.net.McpClient() }

    // ViewModel
    viewModel { ChatViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { AssistantViewModel(get(), get(), get()) }
    viewModel { com.isledeer.soullink.ui.worldbook.WorldbookViewModel(get(), get(), get(), get(), get(), get()) }
}