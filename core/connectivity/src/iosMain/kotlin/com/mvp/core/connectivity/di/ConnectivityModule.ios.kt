package com.mvp.core.connectivity.di

import com.mvp.core.common.domain.connectivity.ConnectivityObserver
import com.mvp.core.connectivity.NetworkPathConnectivityObserver
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val connectivityPlatformModule: Module = module {
    single<ConnectivityObserver> { NetworkPathConnectivityObserver() }
}
