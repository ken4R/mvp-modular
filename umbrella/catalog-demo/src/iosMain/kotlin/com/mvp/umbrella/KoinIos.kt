package com.mvp.umbrella

import com.mvp.core.common.di.coreCommonModule
import com.mvp.core.connectivity.di.connectivityModule
import com.mvp.core.database.di.databaseModule
import com.mvp.core.network.di.networkModule
import com.mvp.feature.catalog.di.catalogModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/** Modules Koin de CatalogDemo : ceux des modules du parapluie, vérifiés par UmbrellaKoinTest. */
internal val umbrellaModules: List<Module> = listOf(
    coreCommonModule,
    networkModule,
    databaseModule,
    connectivityModule,
    catalogModule,
)

/** Appelé une seule fois au lancement de l'app de démo. */
fun doInitKoin() {
    startKoin { modules(umbrellaModules) }
}
