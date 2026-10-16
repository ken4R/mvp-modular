package com.mvp.core.common.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.common.dispatcher.DefaultAppDispatchers
import org.koin.dsl.module

/** Dépendances fournies par le module commun. */
val coreCommonModule = module {
    single<AppDispatchers> { DefaultAppDispatchers() }
}
