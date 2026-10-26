package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.notification.NotificationsPreference
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.SessionExpiry
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.model.LocationReminder
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.reminder.LocationReminderScheduler
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException

/**
 * Garde les rappels par lieu du système alignés sur les tâches (ADR 0023), comme [SyncTodoRemindersUseCase] pour
 * les rappels à l'heure : à chaque changement des tâches, du réglage Notifications, de la session ou des permissions
 * de localisation, les zones posées sont remplacées par celles des tâches à faire (au plus
 * [LocationReminder.MAX_ACTIVE]), jusqu'à la fin de la session. Notifications coupées ou permissions manquantes :
 * aucune. Sans session : toutes retirées, notifications affichées comprises. À collecter pendant toute la vie de
 * l'app ; chaque émission donne le nombre de rappels posés.
 */
class SyncTodoLocationRemindersUseCase(
    private val repository: TodoRepository,
    private val notifications: NotificationsPreference,
    private val session: SessionExpiry,
    private val scheduler: LocationReminderScheduler,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, Int>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<Int>> =
        combine(
            repository.observeTodos(),
            notifications.observeNotificationsEnabled(),
            session.observeExpiresAt(),
            scheduler.observeAvailability(),
        ) { todos, enabled, expiresAt, available -> pending(todos, enabled && available, expiresAt) }
            .distinctUntilChanged()
            .map { pending ->
                // Un échec du système ne coupe pas la synchronisation : la prochaine émission réessaie.
                try {
                    if (pending == null) scheduler.cancelAll() else scheduler.replaceAll(pending.todos, pending.expiresAt)
                    AppResult.Success(pending?.todos?.size ?: 0)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    AppResult.Failure(AppError.Unknown(e.message))
                }
            }

    private data class Pending(val todos: List<Todo>, val expiresAt: Long)

    /** Rappels à poser, ou `null` sans session (tout retirer). */
    private fun pending(todos: List<Todo>, deliverable: Boolean, expiresAt: Long?): Pending? {
        if (expiresAt == null) return null
        if (!deliverable) return Pending(emptyList(), expiresAt)
        val withPlace = todos.filter { !it.isDone && it.locationReminder != null }.take(LocationReminder.MAX_ACTIVE)
        return Pending(withPlace, expiresAt)
    }
}
