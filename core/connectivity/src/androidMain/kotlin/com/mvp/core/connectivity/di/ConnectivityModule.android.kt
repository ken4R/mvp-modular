package com.mvp.core.connectivity.di

import android.content.Context
import com.mvp.core.common.domain.connectivity.ConnectivityObserver
import com.mvp.core.connectivity.AndroidConnectivityObserver
import org.koin.core.module.Module
import org.koin.dsl.module

/** Le `Context` vient de `androidContext(...)`, passé à `initKoin` par l'application. */
internal actual val connectivityPlatformModule: Module = module {
    single<ConnectivityObserver> { AndroidConnectivityObserver(get<Context>()) }
}
