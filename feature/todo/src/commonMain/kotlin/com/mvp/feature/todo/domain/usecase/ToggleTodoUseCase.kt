package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher

/** Passe une tâche de "à faire" à "terminée" et inversement. Paramètre : id de la tâche. */
class ToggleTodoUseCase(
    private val repository: TodoRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<String, Todo>(dispatcher) {

    override suspend fun execute(params: String): AppResult<Todo> = repository.toggleTodo(params)
}
