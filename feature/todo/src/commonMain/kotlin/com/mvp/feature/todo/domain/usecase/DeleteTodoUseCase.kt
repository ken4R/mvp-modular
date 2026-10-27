package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Supprime une tâche : elle part dans la corbeille (ADR 0025), d'où [RestoreTodoUseCase] la fait revenir (bouton
 * « Annuler » juste après, ou écran Corbeille pendant 30 jours). Paramètre : id de la tâche. Ses rappels sont
 * annulés tant qu'elle y est ; ses pièces jointes sont gardées.
 */
class DeleteTodoUseCase(
    private val repository: TodoRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<String, Unit>(dispatcher) {

    override suspend fun execute(params: String): AppResult<Unit> = repository.deleteTodo(params)
}
