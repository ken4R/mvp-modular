package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first

/**
 * Tâche demandée par son id, par exemple par un lien profond. Paramètre : id de la tâche.
 * Règle métier : une tâche absente (supprimée, ou d'un autre compte) renvoie [AppError.NotFound].
 */
class FindTodoUseCase(
    private val repository: TodoRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<String, Todo>(dispatcher) {

    override suspend fun execute(params: String): AppResult<Todo> =
        repository.observeTodos().first().find { it.id == params }?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.NotFound)
}
