package com.isledeer.soullink.di

import com.isledeer.soullink.data.AppDatabase
import com.isledeer.soullink.data.SettingsRepository
import com.isledeer.soullink.net.SseClient
import com.isledeer.soullink.ui.ChatViewModel
import com.isledeer.soullink.ui.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // 存储
    single { AppDatabase.get(androidContext()) }
    single { get<AppDatabase>().messageDao() }
    single { SettingsRepository(androidContext()) }

    // 网络
    single { SseClient() }

    // ViewModel
    viewModel { ChatViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
}