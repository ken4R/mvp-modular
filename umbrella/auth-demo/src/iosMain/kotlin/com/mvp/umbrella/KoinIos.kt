package com.mvp.umbrella

import com.mvp.core.common.di.coreCommonModule
import com.mvp.core.securestorage.di.secureStorageModule
import com.mvp.feature.auth.di.authModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/** Modules Koin de AuthDemo : ceux des modules du parapluie, vérifiés par UmbrellaKoinTest. */
internal val umbrellaModules: List<Module> = listOf(
    coreCommonModule,
    secureStorageModule,
    authModule,
)

/** Appelé une seule fois au lancement de l'app de démo. */
fun doInitKoin() {
    startKoin { modules(umbrellaModules) }
}
