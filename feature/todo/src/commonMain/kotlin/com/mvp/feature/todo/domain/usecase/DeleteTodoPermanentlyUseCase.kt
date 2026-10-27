package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.repository.TodoTrashRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Supprime définitivement une tâche de la corbeille (ADR 0025), pièces jointes comprises ; la suppression part au
 * serveur. Paramètre : id de la tâche. Règle métier : seule une tâche de la corbeille peut l'être (sinon NotFound).
 */
class DeleteTodoPermanentlyUseCase(
    private val repository: TodoTrashRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<String, Unit>(dispatcher) {

    override suspend fun execute(params: String): AppResult<Unit> = repository.deletePermanently(params)
}
