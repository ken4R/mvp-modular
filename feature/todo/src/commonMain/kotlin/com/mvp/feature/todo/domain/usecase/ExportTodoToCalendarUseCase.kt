package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.core.deeplink.domain.DeepLinks
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.feature.todo.domain.calendar.CalendarDate
import com.mvp.feature.todo.domain.model.TodoCalendarEvent
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first

/**
 * Événement à proposer au calendrier du téléphone pour la tâche d'id `params` (ADR 0022) : une journée entière le
 * jour de l'échéance, le titre et le lien de la tâche. Règles métier : jamais la note, ni les pièces jointes, ni le
 * rappel ; une tâche sans échéance renvoie [AppError.Validation], une tâche absente [AppError.NotFound]. L'app
 * n'écrit rien elle-même : l'utilisateur confirme dans l'écran d'ajout du système.
 */
class ExportTodoToCalendarUseCase(
    private val repository: TodoRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<String, TodoCalendarEvent>(dispatcher) {

    override suspend fun execute(params: String): AppResult<TodoCalendarEvent> {
        val todo = repository.observeTodos().first().find { it.id == params }
            ?: return AppResult.Failure(AppError.NotFound)
        val dueDate = todo.dueDate ?: return AppResult.Failure(AppError.Validation("Choisissez d'abord une échéance"))
        return AppResult.Success(
            TodoCalendarEvent(
                title = todo.title.lines().joinToString(" ") { it.trim() }.trim(),
                date = CalendarDate.fromEpochDay(dueDate),
                link = DeepLinks.url(DeepLink.Tasks(todo.id)),
            ),
        )
    }
}
