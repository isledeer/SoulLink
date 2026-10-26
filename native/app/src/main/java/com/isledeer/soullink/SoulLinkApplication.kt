package com.isledeer.soullink

import android.app.Application
import com.isledeer.soullink.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SoulLinkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SoulLinkApplication)
            modules(appModule)
        }
    }
}