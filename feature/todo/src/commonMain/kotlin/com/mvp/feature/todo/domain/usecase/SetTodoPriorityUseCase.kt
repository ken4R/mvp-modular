package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoPriorityChange
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher

/** Change la priorité d'une tâche (ADR 0024) ; elle est synchronisée avec la tâche, comme le titre. */
class SetTodoPriorityUseCase(
    private val repository: TodoRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<TodoPriorityChange, Todo>(dispatcher) {

    override suspend fun execute(params: TodoPriorityChange): AppResult<Todo> =
        repository.setPriority(params.todoId, params.priority)
}
