package com.mvp.shared.di

import com.mvp.core.common.di.coreCommonModule
import com.mvp.core.database.di.databaseModule
import com.mvp.core.location.di.locationModule
import com.mvp.core.network.di.networkModule
import com.mvp.core.notifications.di.notificationsModule
import com.mvp.core.securestorage.di.secureStorageModule
import com.mvp.feature.auth.di.authModule
import com.mvp.feature.catalog.di.catalogModule
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
    secureStorageModule,
    notificationsModule,
    locationModule,
    todoModule,
    weatherModule,
    settingsModule,
    authModule,
    catalogModule,
)

fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(sharedModules)
}
