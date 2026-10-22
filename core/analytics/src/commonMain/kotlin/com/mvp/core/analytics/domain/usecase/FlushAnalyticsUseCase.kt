package com.mvp.core.analytics.domain.usecase

import com.mvp.core.analytics.domain.repository.AnalyticsRepository
import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Envoie les événements en attente : appelé quand l'app passe en arrière-plan, et depuis le journal de debug.
 * Renvoie le nombre d'événements envoyés. En cas d'échec, ils restent en attente pour le prochain envoi.
 */
class FlushAnalyticsUseCase(
    private val repository: AnalyticsRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Int>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Int> = safeCall { repository.flush() }
}
