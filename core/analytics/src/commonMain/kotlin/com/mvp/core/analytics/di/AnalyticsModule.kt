package com.mvp.core.analytics.di

import com.mvp.core.analytics.data.QueuedAnalyticsRepository
import com.mvp.core.analytics.data.session.AnalyticsUserSessionObserver
import com.mvp.core.analytics.data.sink.AnalyticsSink
import com.mvp.core.analytics.data.sink.DebugAnalyticsSink
import com.mvp.core.analytics.domain.repository.AnalyticsRepository
import com.mvp.core.analytics.domain.usecase.FlushAnalyticsUseCase
import com.mvp.core.analytics.domain.usecase.ObserveAnalyticsJournalUseCase
import com.mvp.core.analytics.domain.usecase.TrackEventUseCase
import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.common.domain.analytics.AnalyticsConsentObserver
import com.mvp.core.common.domain.analytics.AnalyticsTracker
import com.mvp.core.common.domain.session.UserSessionObserver
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class)
private val systemNow: () -> Long = { Clock.System.now().toEpochMilliseconds() }

@OptIn(ExperimentalUuidApi::class)
private val randomSessionId: () -> String = { Uuid.random().toString() }

val analyticsModule = module {
    // Data. Pas encore d'outil de mesure : journal local. Un vrai outil remplacera DebugAnalyticsSink ici.
    single { DebugAnalyticsSink() } bind AnalyticsSink::class
    // AnalyticsConsent vient de feature:settings, sans dépendance directe. Une seule file pour toute l'app.
    single {
        QueuedAnalyticsRepository(
            consent = get(),
            sink = get(),
            sentEvents = get<DebugAnalyticsSink>().observeSent(),
            now = systemNow,
            newSessionId = randomSessionId,
        )
    } binds arrayOf(AnalyticsRepository::class, AnalyticsTracker::class, AnalyticsConsentObserver::class)
    // Prévenu par feature:auth : la mesure est remise à zéro à la déconnexion.
    single { AnalyticsUserSessionObserver(get()) } bind UserSessionObserver::class

    // Domain
    factory { TrackEventUseCase(get(), get<AppDispatchers>().default) }
    factory { FlushAnalyticsUseCase(get(), get<AppDispatchers>().default) }
    factory { ObserveAnalyticsJournalUseCase(get(), get<AppDispatchers>().default) }
}
