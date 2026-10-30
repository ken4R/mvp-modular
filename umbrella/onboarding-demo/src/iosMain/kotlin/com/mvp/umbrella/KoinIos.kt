package com.mvp.umbrella

import com.mvp.core.common.di.coreCommonModule
import com.mvp.core.database.di.databaseModule
import com.mvp.core.securestorage.di.secureStorageModule
import com.mvp.feature.onboarding.di.onboardingModule
import com.mvp.feature.settings.di.settingsModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/** Modules Koin de OnboardingDemo : ceux des modules du parapluie, vérifiés par UmbrellaKoinTest. */
internal val umbrellaModules: List<Module> = listOf(
    coreCommonModule,
    databaseModule,
    secureStorageModule,
    settingsModule,
    onboardingModule,
)

/** Appelé une seule fois au lancement de l'app de démo. */
fun doInitKoin() {
    startKoin { modules(umbrellaModules) }
}
