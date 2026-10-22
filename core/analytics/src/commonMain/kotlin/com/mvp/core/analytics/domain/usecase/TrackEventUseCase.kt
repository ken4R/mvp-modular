package com.mvp.core.analytics.domain.usecase

import com.mvp.core.common.domain.analytics.AnalyticsEvent
import com.mvp.core.common.domain.analytics.AnalyticsTracker
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Mesure un événement venu de l'UI (onglet affiché...). Règle métier : sans consentement valide,
 * rien n'est gardé ; l'appelant n'a pas à le vérifier.
 */
class TrackEventUseCase(
    private val tracker: AnalyticsTracker,
    dispatcher: CoroutineDispatcher,
) : UseCase<AnalyticsEvent, Unit>(dispatcher) {

    override suspend fun execute(params: AnalyticsEvent): AppResult<Unit> {
        tracker.track(params)
        return AppResult.Success(Unit)
    }
}
