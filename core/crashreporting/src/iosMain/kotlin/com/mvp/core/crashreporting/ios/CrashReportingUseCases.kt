package com.mvp.core.crashreporting.ios

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.ios.Subscription
import com.mvp.core.common.ios.SwiftUseCases
import com.mvp.core.crashreporting.domain.model.CrashReport
import com.mvp.core.crashreporting.domain.model.TestCrashException
import com.mvp.core.crashreporting.domain.usecase.ObserveCrashJournalUseCase
import com.mvp.core.crashreporting.domain.usecase.StartCrashReportingUseCase
import org.koin.core.component.get

/**
 * Point d'accès Swift aux use cases du suivi des plantages.
 * Koin n'est pas utilisable directement depuis Swift. Les use cases suspend s'appellent en `async` côté Swift ;
 * les Flow passent par des callbacks (voir SwiftUseCases).
 */
object CrashReportingUseCases : SwiftUseCases() {
    fun startCrashReporting(): StartCrashReportingUseCase = get()

    /**
     * « Provoquer un plantage » des builds de debug : exception Kotlin levée vers Swift sans `@Throws`, donc non
     * rattrapable, comme un vrai plantage KMP. L'app s'arrête ; le rapport part au lancement suivant (ADR 0020).
     */
    fun crashForTest(): Unit = throw TestCrashException()

    /** Journal des rapports de plantage (builds de debug). Les callbacks sont appelés sur le main thread. */
    fun observeCrashJournal(onJournal: (List<CrashReport>) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveCrashJournalUseCase>()(NoParams).subscribe(onJournal, onError)
}
