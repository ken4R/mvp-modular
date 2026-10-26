package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.location.LocationAccuracy
import com.mvp.core.common.domain.location.LocationProvider
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.model.LocationReminder
import com.mvp.feature.todo.domain.model.LocationReminderHere
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first

/**
 * « Me le rappeler en arrivant ici » (ADR 0023) : pose un rappel par lieu sur la position actuelle, précise, de
 * l'appareil. Règles : rayon parmi [LocationReminder.RADII], nom de [LocationReminder.MAX_LABEL_LENGTH] caractères au
 * plus, au plus [LocationReminder.MAX_ACTIVE] tâches à faire avec un rappel par lieu. La position ne quitte pas
 * l'appareil. Échoue avec [AppError.PermissionDenied] sans la position exacte, [AppError.Unavailable] sans mesure.
 * La notification elle-même est posée par [SyncTodoLocationRemindersUseCase].
 */
class SetLocationReminderHereUseCase(
    private val repository: TodoRepository,
    private val location: LocationProvider,
    dispatcher: CoroutineDispatcher,
) : UseCase<LocationReminderHere, Todo>(dispatcher) {

    override suspend fun execute(params: LocationReminderHere): AppResult<Todo> {
        if (params.radiusMeters !in LocationReminder.RADII) return AppResult.Failure(AppError.Validation("Rayon non proposé"))
        val label = params.label.trim().ifEmpty { LocationReminder.DEFAULT_LABEL }
        if (label.length > LocationReminder.MAX_LABEL_LENGTH) {
            return AppResult.Failure(AppError.Validation("Nom du lieu trop long (${LocationReminder.MAX_LABEL_LENGTH} caractères au plus)"))
        }
        val todos = repository.observeTodos().first()
        val todo = todos.firstOrNull { it.id == params.todoId } ?: return AppResult.Failure(AppError.NotFound)
        if (todo.isDone) return AppResult.Failure(AppError.Validation("La tâche est déjà terminée"))
        val others = todos.count { it.id != todo.id && !it.isDone && it.locationReminder != null }
        if (others >= LocationReminder.MAX_ACTIVE) {
            return AppResult.Failure(AppError.Validation("${LocationReminder.MAX_ACTIVE} rappels par lieu au maximum"))
        }
        return when (val position = location.currentPosition(LocationAccuracy.Precise)) {
            is AppResult.Failure -> position
            is AppResult.Success -> repository.setLocationReminder(
                todo.id,
                LocationReminder(position.data.latitude, position.data.longitude, params.radiusMeters, label),
            )
        }
    }
}
