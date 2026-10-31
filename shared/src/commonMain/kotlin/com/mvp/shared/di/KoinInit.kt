package com.mvp.shared.di

import com.mvp.core.analytics.di.analyticsModule
import com.mvp.core.common.di.coreCommonModule
import com.mvp.core.connectivity.di.connectivityModule
import com.mvp.core.crashreporting.di.crashReportingModule
import com.mvp.core.database.di.databaseModule
import com.mvp.core.deeplink.di.deepLinkModule
import com.mvp.core.featureflags.di.featureFlagsModule
import com.mvp.core.files.di.filesModule
import com.mvp.core.location.di.locationModule
import com.mvp.core.network.di.networkModule
import com.mvp.core.notifications.di.notificationsModule
import com.mvp.core.securestorage.di.secureStorageModule
import com.mvp.feature.auth.di.authModule
import com.mvp.feature.catalog.di.catalogModule
import com.mvp.feature.focus.di.focusModule
import com.mvp.feature.onboarding.di.onboardingModule
import com.mvp.feature.settings.di.settingsModule
import com.mvp.feature.todo.di.todoModule
import com.mvp.feature.weather.di.weatherModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/**
 * Point de coordination : liste de tous les modules KMP du projet.
 * Chaque nouveau module (feature, design system, network...) expose son module Koin
 * et l'ajoute ici, sans que les apps Android / iOS aient à changer.
 */
val sharedModules: List<Module> = listOf(
    coreCommonModule,
    networkModule,
    databaseModule,
    filesModule,
    secureStorageModule,
    notificationsModule,
    locationModule,
    deepLinkModule,
    analyticsModule,
    crashReportingModule,
    connectivityModule,
    featureFlagsModule,
    todoModule,
    weatherModule,
    settingsModule,
    authModule,
    catalogModule,
    onboardingModule,
    focusModule,
)

fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(sharedModules)
}
