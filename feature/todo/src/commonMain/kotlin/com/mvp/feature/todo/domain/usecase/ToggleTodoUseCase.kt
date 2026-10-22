package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.analytics.AnalyticsEvent
import com.mvp.core.common.domain.analytics.AnalyticsTracker
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Passe une tâche de "à faire" à "terminée" et inversement. Paramètre : id de la tâche.
 * Une tâche terminée est mesurée (sans son titre), si l'utilisateur a accepté la mesure d'audience.
 */
class ToggleTodoUseCase(
    private val repository: TodoRepository,
    private val analytics: AnalyticsTracker,
    dispatcher: CoroutineDispatcher,
) : UseCase<String, Todo>(dispatcher) {

    override suspend fun execute(params: String): AppResult<Todo> = repository.toggleTodo(params).also { result ->
        if (result is AppResult.Success && result.data.isDone) analytics.track(AnalyticsEvent.TaskCompleted)
    }
}
