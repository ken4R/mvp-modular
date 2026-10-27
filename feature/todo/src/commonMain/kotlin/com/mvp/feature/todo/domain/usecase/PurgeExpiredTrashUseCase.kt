package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.model.TodoTrash
import com.mvp.feature.todo.domain.repository.TodoTrashRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Supprime définitivement les tâches de la corbeille depuis plus de [TodoTrash.RETENTION_DAYS] jours (ADR 0025).
 * Lancé par chaque app au démarrage et à l'ouverture de la corbeille. Renvoie le nombre de tâches supprimées.
 * [now] : heure courante (epoch ms).
 */
class PurgeExpiredTrashUseCase(
    private val repository: TodoTrashRepository,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Int>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Int> =
        repository.deleteTrashedBefore(TodoTrash.expiryCutoff(now()))
}
