package com.mvp.core.connectivity.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.connectivity.domain.usecase.ObserveConnectivityUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

/** Fournit le [com.mvp.core.common.domain.connectivity.ConnectivityObserver] de la plateforme. */
internal expect val connectivityPlatformModule: Module

val connectivityModule = module {
    includes(connectivityPlatformModule)

    // Domain
    factory { ObserveConnectivityUseCase(get(), get<AppDispatchers>().default) }
}
