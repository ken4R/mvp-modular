package com.mvp.feature.settings.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.common.domain.analytics.AnalyticsConsent
import com.mvp.core.common.domain.notification.NotificationsPreference
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.core.database.DatabaseDriverFactory
import com.mvp.feature.settings.data.analytics.SettingsAnalyticsConsent
import com.mvp.feature.settings.data.db.SettingsDatabase
import com.mvp.feature.settings.data.notification.SettingsNotificationsPreference
import com.mvp.feature.settings.data.repository.AppLockRepositoryImpl
import com.mvp.feature.settings.data.repository.SettingsRepositoryImpl
import com.mvp.feature.settings.data.session.SettingsUserSessionObserver
import com.mvp.feature.settings.data.source.ProfileLocalDataSource
import com.mvp.feature.settings.data.source.SecureProfileLocalDataSource
import com.mvp.feature.settings.data.source.SettingsLocalDataSource
import com.mvp.feature.settings.data.source.SqlDelightSettingsLocalDataSource
import com.mvp.feature.settings.domain.repository.AppLockRepository
import com.mvp.feature.settings.domain.repository.SettingsRepository
import com.mvp.feature.settings.domain.usecase.LockAppUseCase
import com.mvp.feature.settings.domain.usecase.ObserveAppLockUseCase
import com.mvp.feature.settings.domain.usecase.ObserveSettingsUseCase
import com.mvp.feature.settings.domain.usecase.SetAppLockEnabledUseCase
import com.mvp.feature.settings.domain.usecase.SetConsentUseCase
import com.mvp.feature.settings.domain.usecase.SetNotificationsEnabledUseCase
import com.mvp.feature.settings.domain.usecase.UnlockAppUseCase
import com.mvp.feature.settings.domain.usecase.UpdateProfileUseCase
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private val systemNow: () -> Long = { Clock.System.now().toEpochMilliseconds() }

val settingsModule = module {
    // Data
    single { SettingsDatabase(get<DatabaseDriverFactory>().create(SettingsDatabase.Schema, "settings.db")) }
    single<SettingsLocalDataSource> { SqlDelightSettingsLocalDataSource(get(), get<AppDispatchers>().io) }
    // Profil : stockage chiffré (core:securestorage), les autres réglages restent en SQLite.
    single<ProfileLocalDataSource> { SecureProfileLocalDataSource(get(), legacy = get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get(), get()) }
    // Un seul état de verrou pour toute l'app (single) : verrouillée à chaque lancement.
    single<AppLockRepository> { AppLockRepositoryImpl(get()) }
    // Prévenu par feature:auth à la connexion et à la déconnexion.
    single { SettingsUserSessionObserver(get(), get()) } bind UserSessionObserver::class
    // Lu par feature:todo : aucun rappel ne part tant que les notifications sont coupées.
    single<NotificationsPreference> { SettingsNotificationsPreference(get()) }
    // Lu par core:analytics avant de garder ou d'envoyer un événement.
    single<AnalyticsConsent> { SettingsAnalyticsConsent(get()) }

    // Domain
    factory { ObserveSettingsUseCase(get(), get<AppDispatchers>().io) }
    factory { UpdateProfileUseCase(get(), get<AppDispatchers>().io) }
    factory { SetNotificationsEnabledUseCase(get(), get<AppDispatchers>().io) }
    // AnalyticsConsentObserver vient de core:analytics : retirer le consentement vide sa file d'attente.
    factory { SetConsentUseCase(get(), getAll(), systemNow, get<AppDispatchers>().io) }
    factory { ObserveAppLockUseCase(get(), get<AppDispatchers>().io) }
    factory { SetAppLockEnabledUseCase(get(), get(), get<AppDispatchers>().io) }
    factory { LockAppUseCase(get(), get<AppDispatchers>().io) }
    factory { UnlockAppUseCase(get(), get<AppDispatchers>().io) }
}
