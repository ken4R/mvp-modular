package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.repository.TodoTrashRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Sort une tâche de la corbeille (ADR 0025) : « Annuler » après une suppression, ou « Restaurer » dans la corbeille.
 * Paramètre : id de la tâche. Elle revient à sa place, avec ses pièces jointes et ses rappels encore à venir.
 * Règle métier : une tâche absente de la corbeille (déjà restaurée, supprimée définitivement) renvoie NotFound.
 */
class RestoreTodoUseCase(
    private val repository: TodoTrashRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<String, Todo>(dispatcher) {

    override suspend fun execute(params: String): AppResult<Todo> = repository.restore(params)
}
