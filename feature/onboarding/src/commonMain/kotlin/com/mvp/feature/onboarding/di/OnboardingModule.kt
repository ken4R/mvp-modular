package com.mvp.feature.onboarding.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.database.DatabaseDriverFactory
import com.mvp.feature.onboarding.data.db.OnboardingDatabase
import com.mvp.feature.onboarding.data.repository.OnboardingRepositoryImpl
import com.mvp.feature.onboarding.data.source.OnboardingLocalDataSource
import com.mvp.feature.onboarding.data.source.SqlDelightOnboardingLocalDataSource
import com.mvp.feature.onboarding.domain.repository.OnboardingRepository
import com.mvp.feature.onboarding.domain.usecase.CompleteOnboardingUseCase
import com.mvp.feature.onboarding.domain.usecase.GetOnboardingStepsUseCase
import com.mvp.feature.onboarding.domain.usecase.ObserveOnboardingRequiredUseCase
import com.mvp.feature.onboarding.domain.usecase.RestartOnboardingUseCase
import org.koin.dsl.module

/**
 * Présentation du premier lancement (ADR 0016). Volontairement sans `UserSessionObserver` : vue une fois par
 * appareil, elle n'est pas remontrée après une déconnexion. Les autorisations elles-mêmes sont enregistrées par
 * feature:settings (notifications, verrou, consentement) et par le système (position).
 */
val onboardingModule = module {
    // Data
    single { OnboardingDatabase(get<DatabaseDriverFactory>().create(OnboardingDatabase.Schema, "onboarding.db")) }
    single<OnboardingLocalDataSource> { SqlDelightOnboardingLocalDataSource(get(), get<AppDispatchers>().io) }
    single<OnboardingRepository> { OnboardingRepositoryImpl(get()) }

    // Domain
    factory { ObserveOnboardingRequiredUseCase(get(), get<AppDispatchers>().io) }
    factory { CompleteOnboardingUseCase(get(), get<AppDispatchers>().io) }
    factory { RestartOnboardingUseCase(get(), get<AppDispatchers>().io) }
    factory { GetOnboardingStepsUseCase(get<AppDispatchers>().io) }
}
