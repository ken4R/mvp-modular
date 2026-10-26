package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher

/** Retire le rappel par lieu de la tâche (paramètre : son id). La position gardée est effacée avec lui. */
class RemoveLocationReminderUseCase(
    private val repository: TodoRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<String, Todo>(dispatcher) {

    override suspend fun execute(params: String): AppResult<Todo> = repository.setLocationReminder(params, null)
}
