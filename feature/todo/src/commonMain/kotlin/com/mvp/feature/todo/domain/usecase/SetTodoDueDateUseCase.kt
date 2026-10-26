package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.calendar.CalendarDate
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoDueDate
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Pose ou retire (`dueDate = null`) l'échéance d'une tâche (ADR 0022). Règle métier : une date entre 2000 et 2100 ;
 * une date passée est acceptée (tâche déjà en retard). L'échéance est synchronisée avec la tâche, contrairement au
 * rappel qui reste propre à l'appareil.
 */
class SetTodoDueDateUseCase(
    private val repository: TodoRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<TodoDueDate, Todo>(dispatcher) {

    override suspend fun execute(params: TodoDueDate): AppResult<Todo> {
        val dueDate = params.dueDate
        if (dueDate != null && !CalendarDate.isValidDueDate(dueDate)) {
            return AppResult.Failure(
                AppError.Validation("Choisissez une date entre ${CalendarDate.MIN_YEAR} et ${CalendarDate.MAX_YEAR}"),
            )
        }
        return repository.setDueDate(params.todoId, dueDate)
    }
}
