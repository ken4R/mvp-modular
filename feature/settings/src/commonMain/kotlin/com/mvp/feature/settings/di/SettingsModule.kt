package com.mvp.feature.settings.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.feature.settings.data.repository.SettingsRepositoryImpl
import com.mvp.feature.settings.data.source.InMemorySettingsLocalDataSource
import com.mvp.feature.settings.data.source.SettingsLocalDataSource
import com.mvp.feature.settings.domain.repository.SettingsRepository
import com.mvp.feature.settings.domain.usecase.ObserveSettingsUseCase
import com.mvp.feature.settings.domain.usecase.SetConsentUseCase
import com.mvp.feature.settings.domain.usecase.SetNotificationsEnabledUseCase
import com.mvp.feature.settings.domain.usecase.UpdateProfileUseCase
import org.koin.dsl.module

val settingsModule = module {
    // Data
    single<SettingsLocalDataSource> { InMemorySettingsLocalDataSource() }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }

    // Domain
    factory { ObserveSettingsUseCase(get(), get<AppDispatchers>().io) }
    factory { UpdateProfileUseCase(get(), get<AppDispatchers>().io) }
    factory { SetNotificationsEnabledUseCase(get(), get<AppDispatchers>().io) }
    factory { SetConsentUseCase(get(), get<AppDispatchers>().io) }
}
