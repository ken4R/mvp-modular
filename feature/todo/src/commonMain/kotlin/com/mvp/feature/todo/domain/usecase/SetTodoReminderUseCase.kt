package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoReminder
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Pose ou retire (`at = null`) le rappel d'une tâche. Règle métier : la date doit être à venir.
 * La notification elle-même est programmée par [SyncTodoRemindersUseCase].
 */
class SetTodoReminderUseCase(
    private val repository: TodoRepository,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : UseCase<TodoReminder, Todo>(dispatcher) {

    override suspend fun execute(params: TodoReminder): AppResult<Todo> {
        val at = params.at
        if (at != null && at <= now()) return AppResult.Failure(AppError.Validation("Choisissez une date à venir"))
        return repository.setReminder(params.todoId, at)
    }
}
