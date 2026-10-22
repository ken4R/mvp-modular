package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.connectivity.ConnectivityObserver
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.repository.TodoSyncRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first

/**
 * Synchronise tout de suite (bouton « Réessayer » du bandeau), sans attendre la prochaine tentative
 * automatique. Hors ligne, échoue avec [AppError.Network] sans appeler le serveur. Renvoie le nombre de
 * modifications encore en attente.
 */
class SyncTodosUseCase(
    private val sync: TodoSyncRepository,
    private val connectivity: ConnectivityObserver,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Int>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Int> =
        if (connectivity.observeIsOnline().first()) sync.sync() else AppResult.Failure(AppError.Network)
}
