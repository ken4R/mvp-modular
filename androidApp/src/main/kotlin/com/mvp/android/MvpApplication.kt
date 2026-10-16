package com.mvp.android

import android.app.Application
import com.mvp.android.di.androidAppModule
import com.mvp.shared.di.initKoin
import org.koin.android.ext.koin.androidContext

class MvpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@MvpApplication)
            modules(androidAppModule)
        }
    }
}
