package com.mvp.umbrella

import com.mvp.core.analytics.di.analyticsModule
import com.mvp.core.common.di.coreCommonModule
import com.mvp.core.connectivity.di.connectivityModule
import com.mvp.core.database.di.databaseModule
import com.mvp.core.deeplink.di.deepLinkModule
import com.mvp.core.files.di.filesModule
import com.mvp.core.location.di.locationModule
import com.mvp.core.notifications.di.notificationsModule
import com.mvp.core.securestorage.di.secureStorageModule
import com.mvp.feature.auth.di.authModule
import com.mvp.feature.settings.di.settingsModule
import com.mvp.feature.todo.di.todoModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/** Modules Koin de TodoDemo : ceux des modules du parapluie, vérifiés par UmbrellaKoinTest. */
internal val umbrellaModules: List<Module> = listOf(
    coreCommonModule,
    databaseModule,
    filesModule,
    secureStorageModule,
    notificationsModule,
    locationModule,
    deepLinkModule,
    analyticsModule,
    connectivityModule,
    todoModule,
    settingsModule,
    authModule,
)

/** Appelé une seule fois au lancement de l'app de démo. */
fun doInitKoin() {
    startKoin { modules(umbrellaModules) }
}
