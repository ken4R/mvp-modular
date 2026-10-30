package com.mvp.core.analytics.ios

import com.mvp.core.analytics.domain.model.AnalyticsJournal
import com.mvp.core.analytics.domain.usecase.FlushAnalyticsUseCase
import com.mvp.core.analytics.domain.usecase.ObserveAnalyticsJournalUseCase
import com.mvp.core.analytics.domain.usecase.TrackEventUseCase
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.ios.Subscription
import com.mvp.core.common.ios.SwiftUseCases
import org.koin.core.component.get

/**
 * Point d'accès Swift aux use cases de la mesure d'audience.
 * Koin n'est pas utilisable directement depuis Swift. Les use cases suspend s'appellent en `async` côté Swift ;
 * les Flow passent par des callbacks (voir SwiftUseCases).
 */
object AnalyticsUseCases : SwiftUseCases() {
    fun trackEvent(): TrackEventUseCase = get()
    fun flushAnalytics(): FlushAnalyticsUseCase = get()

    /** Journal de mesure (builds de debug). Les callbacks sont appelés sur le main thread. */
    fun observeAnalyticsJournal(onJournal: (AnalyticsJournal) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveAnalyticsJournalUseCase>()(NoParams).subscribe(onJournal, onError)
}
