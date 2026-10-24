package com.mvp.core.featureflags.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.map
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.core.featureflags.domain.model.RemoteFlagsRefresh
import com.mvp.core.featureflags.domain.repository.FeatureFlagRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Rafraîchit les flags distants, appelé au lancement et au retour au premier plan : au plus une fois toutes les
 * [minIntervalMillis] d'après l'heure gardée dans le cache, sauf [RemoteFlagsRefresh.force] (menu debug). Renvoie `true` si le
 * distant a été lu. Un échec garde le cache : l'app continue avec les dernières valeurs connues.
 */
class RefreshRemoteFlagsUseCase(
    private val repository: FeatureFlagRepository,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
) : UseCase<RemoteFlagsRefresh, Boolean>(dispatcher) {

    override suspend fun execute(params: RemoteFlagsRefresh): AppResult<Boolean> {
        val last = repository.lastFetchedAt()
        // Une heure dans le futur (horloge de l'appareil reculée) ne bloque pas le rafraîchissement.
        val elapsed = last?.let { now() - it }
        if (!params.force && elapsed != null && elapsed in 0 until minIntervalMillis) return AppResult.Success(false)
        return repository.fetchRemote().map { true }
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MILLIS = 15 * 60 * 1000L
    }
}
