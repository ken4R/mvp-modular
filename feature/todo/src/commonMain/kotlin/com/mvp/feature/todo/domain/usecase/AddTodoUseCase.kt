package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.analytics.AnalyticsEvent
import com.mvp.core.common.domain.analytics.AnalyticsTracker
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Ajoute une tâche. Règle métier : titre non vide, 80 caractères max.
 * L'ajout est mesuré (sans le titre), si l'utilisateur a accepté la mesure d'audience.
 */
class AddTodoUseCase(
    private val repository: TodoRepository,
    private val analytics: AnalyticsTracker,
    dispatcher: CoroutineDispatcher,
) : UseCase<String, Todo>(dispatcher) {

    override suspend fun execute(params: String): AppResult<Todo> {
        val title = params.trim()
        return when {
            title.isEmpty() -> AppResult.Failure(AppError.Validation("Le titre est obligatoire"))
            title.length > MAX_TITLE_LENGTH -> AppResult.Failure(AppError.Validation("$MAX_TITLE_LENGTH caractères maximum"))
            else -> repository.addTodo(title).also { if (it is AppResult.Success) analytics.track(AnalyticsEvent.TaskAdded) }
        }
    }

    companion object {
        const val MAX_TITLE_LENGTH = 80
    }
}
