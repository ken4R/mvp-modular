package com.mvp.umbrella

import com.mvp.core.analytics.di.analyticsModule
import com.mvp.core.common.di.coreCommonModule
import com.mvp.core.crashreporting.di.crashReportingModule
import com.mvp.core.database.di.databaseModule
import com.mvp.core.featureflags.di.featureFlagsModule
import com.mvp.core.files.di.filesModule
import com.mvp.core.network.di.networkModule
import com.mvp.core.securestorage.di.secureStorageModule
import com.mvp.feature.auth.di.authModule
import com.mvp.feature.onboarding.di.onboardingModule
import com.mvp.feature.settings.di.settingsModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/** Modules Koin de SettingsDemo : ceux des modules du parapluie, vérifiés par UmbrellaKoinTest. */
internal val umbrellaModules: List<Module> = listOf(
    coreCommonModule,
    networkModule,
    databaseModule,
    filesModule,
    secureStorageModule,
    analyticsModule,
    crashReportingModule,
    featureFlagsModule,
    settingsModule,
    authModule,
    onboardingModule,
)

/** Appelé une seule fois au lancement de l'app de démo. */
fun doInitKoin() {
    startKoin { modules(umbrellaModules) }
}
