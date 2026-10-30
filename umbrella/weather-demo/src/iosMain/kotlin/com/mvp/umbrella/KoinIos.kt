package com.mvp.umbrella

import com.mvp.core.common.di.coreCommonModule
import com.mvp.core.connectivity.di.connectivityModule
import com.mvp.core.deeplink.di.deepLinkModule
import com.mvp.core.location.di.locationModule
import com.mvp.core.network.di.networkModule
import com.mvp.core.securestorage.di.secureStorageModule
import com.mvp.feature.auth.di.authModule
import com.mvp.feature.weather.di.weatherModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/** Modules Koin de WeatherDemo : ceux des modules du parapluie, vérifiés par UmbrellaKoinTest. */
internal val umbrellaModules: List<Module> = listOf(
    coreCommonModule,
    networkModule,
    secureStorageModule,
    locationModule,
    deepLinkModule,
    connectivityModule,
    weatherModule,
    authModule,
)

/** Appelé une seule fois au lancement de l'app de démo. */
fun doInitKoin() {
    startKoin { modules(umbrellaModules) }
}
