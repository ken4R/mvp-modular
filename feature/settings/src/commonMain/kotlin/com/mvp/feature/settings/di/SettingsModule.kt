package com.mvp.feature.settings.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.database.DatabaseDriverFactory
import com.mvp.feature.settings.data.db.SettingsDatabase
import com.mvp.feature.settings.data.repository.SettingsRepositoryImpl
import com.mvp.feature.settings.data.source.SettingsLocalDataSource
import com.mvp.feature.settings.data.source.SqlDelightSettingsLocalDataSource
import com.mvp.feature.settings.domain.repository.SettingsRepository
import com.mvp.feature.settings.domain.usecase.ObserveSettingsUseCase
import com.mvp.feature.settings.domain.usecase.SetConsentUseCase
import com.mvp.feature.settings.domain.usecase.SetNotificationsEnabledUseCase
import com.mvp.feature.settings.domain.usecase.UpdateProfileUseCase
import org.koin.dsl.module

val settingsModule = module {
    // Data
    single { SettingsDatabase(get<DatabaseDriverFactory>().create(SettingsDatabase.Schema, "settings.db")) }
    single<SettingsLocalDataSource> { SqlDelightSettingsLocalDataSource(get(), get<AppDispatchers>().io) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }

    // Domain
    factory { ObserveSettingsUseCase(get(), get<AppDispatchers>().io) }
    factory { UpdateProfileUseCase(get(), get<AppDispatchers>().io) }
    factory { SetNotificationsEnabledUseCase(get(), get<AppDispatchers>().io) }
    factory { SetConsentUseCase(get(), get<AppDispatchers>().io) }
}
