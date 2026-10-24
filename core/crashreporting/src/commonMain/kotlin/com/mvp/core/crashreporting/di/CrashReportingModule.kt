package com.mvp.core.crashreporting.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.common.domain.crash.CrashReportingConsentObserver
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.core.crashreporting.data.CrashReporter
import com.mvp.core.crashreporting.data.platform.installCrashHandler
import com.mvp.core.crashreporting.data.platform.platformDescription
import com.mvp.core.crashreporting.data.platform.stackFrames
import com.mvp.core.crashreporting.data.session.CrashUserSessionObserver
import com.mvp.core.crashreporting.data.sink.CrashReportSink
import com.mvp.core.crashreporting.data.sink.DebugCrashReportSink
import com.mvp.core.crashreporting.data.store.PendingCrashStore
import com.mvp.core.crashreporting.domain.repository.CrashReportRepository
import com.mvp.core.crashreporting.domain.usecase.ObserveCrashJournalUseCase
import com.mvp.core.crashreporting.domain.usecase.StartCrashReportingUseCase
import com.mvp.core.files.PrivateFileStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
private val randomId: () -> String = { Uuid.random().toString() }

val crashReportingModule = module {
    // Data. Pas encore d'outil de suivi : journal local. Un vrai outil remplacera DebugCrashReportSink ici.
    single { DebugCrashReportSink() } bind CrashReportSink::class
    // Dossier privé hors sauvegardes (core:files) : les rapports y attendent le lancement suivant.
    single { PendingCrashStore(get<PrivateFileStoreFactory>().create("crash_reports")) }
    // CrashReportingConsent vient de feature:settings, sans dépendance directe. Un seul gestionnaire pour l'app.
    single {
        CrashReporter(
            consent = get(),
            store = get(),
            sink = get(),
            journal = get<DebugCrashReportSink>().observeSent(),
            now = systemNow,
            newId = randomId,
            platform = ::platformDescription,
            installHandler = ::installCrashHandler,
            stackFrames = { it.stackFrames() },
            scope = CoroutineScope(SupervisorJob() + get<AppDispatchers>().io),
        )
    } binds arrayOf(CrashReportRepository::class, CrashReportingConsentObserver::class)
    // Prévenu par feature:auth : rapports en attente et journal effacés à la déconnexion.
    single { CrashUserSessionObserver(get()) } bind UserSessionObserver::class

    // Domain : fichiers lus et écrits, d'où le dispatcher d'entrées / sorties.
    factory { StartCrashReportingUseCase(get(), get<AppDispatchers>().io) }
    factory { ObserveCrashJournalUseCase(get(), get<AppDispatchers>().default) }
}
