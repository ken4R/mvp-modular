package com.mvp.feature.focus.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.feature.focus.data.alarm.NotificationFocusAlarm
import com.mvp.feature.focus.data.repository.SecureFocusRepository
import com.mvp.feature.focus.data.session.FocusUserSessionObserver
import com.mvp.feature.focus.domain.model.FocusDurations
import com.mvp.feature.focus.domain.repository.FocusAlarm
import com.mvp.feature.focus.domain.repository.FocusRepository
import com.mvp.feature.focus.domain.usecase.ObserveFocusTimerUseCase
import com.mvp.feature.focus.domain.usecase.ObserveFocusUseCase
import com.mvp.feature.focus.domain.usecase.PauseFocusUseCase
import com.mvp.feature.focus.domain.usecase.ResumeFocusUseCase
import com.mvp.feature.focus.domain.usecase.StartFocusUseCase
import com.mvp.feature.focus.domain.usecase.StartNextFocusPhaseUseCase
import com.mvp.feature.focus.domain.usecase.StopFocusUseCase
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private val systemNow: () -> Long = { Clock.System.now().toEpochMilliseconds() }

/**
 * Mode focus (ADR 0028). Nécessite `SecureStorage` (core:securestorage), `LocalNotificationScheduler`
 * (core:notifications), `NotificationsPreference` et `AppLockPreference` (feature:settings), `SessionExpiry` (feature:auth).
 */
val focusModule = module {
    single { FocusDurations() }

    // Data
    single<FocusRepository> { SecureFocusRepository(get()) }
    single<FocusAlarm> { NotificationFocusAlarm(get(), get(), get()) }
    single { FocusUserSessionObserver(get(), get()) } bind UserSessionObserver::class

    // Domain
    factory { ObserveFocusUseCase(get(), get(), get(), get(), systemNow, get<AppDispatchers>().io) }
    factory { ObserveFocusTimerUseCase(get(), systemNow, get<AppDispatchers>().io) }
    factory { StartFocusUseCase(get(), get(), get(), systemNow, get<AppDispatchers>().io) }
    factory { PauseFocusUseCase(get(), get(), systemNow, get<AppDispatchers>().io) }
    factory { ResumeFocusUseCase(get(), get(), systemNow, get<AppDispatchers>().io) }
    factory { StartNextFocusPhaseUseCase(get(), get(), get(), systemNow, get<AppDispatchers>().io) }
    factory { StopFocusUseCase(get(), get(), get<AppDispatchers>().io) }
}
