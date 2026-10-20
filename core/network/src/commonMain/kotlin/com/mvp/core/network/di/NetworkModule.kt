package com.mvp.core.network.di

import com.mvp.core.network.createHttpClient
import org.koin.dsl.module

/** Un seul client HTTP pour toute l'app. */
val networkModule = module {
    single { createHttpClient() }
}
