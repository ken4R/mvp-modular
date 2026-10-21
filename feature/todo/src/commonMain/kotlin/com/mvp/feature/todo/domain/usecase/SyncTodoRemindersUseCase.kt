package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.notification.NotificationsPreference
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.SessionExpiry
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.reminder.ReminderScheduler
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException

/**
 * Garde les notifications du système alignées sur les tâches : à chaque changement des tâches, du
 * réglage Notifications ou de la session, les rappels programmés sont remplacés par ceux des tâches
 * non terminées dont l'heure est à venir et tombe avant la fin de la session (un rappel ne s'affiche
 * jamais hors session, même app fermée). Notifications coupées : aucun rappel. Sans session : tous
 * les rappels sont annulés, y compris ceux déjà affichés. À collecter pendant toute la vie de l'app ;
 * chaque émission donne le nombre de rappels programmés.
 */
class SyncTodoRemindersUseCase(
    private val repository: TodoRepository,
    private val notifications: NotificationsPreference,
    private val session: SessionExpiry,
    private val scheduler: ReminderScheduler,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, Int>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<Int>> =
        combine(
            repository.observeTodos(),
            notifications.observeNotificationsEnabled(),
            session.observeExpiresAt(),
        ) { todos, enabled, expiresAt -> pendingReminders(todos, enabled, expiresAt) }
            .distinctUntilChanged()
            .map { pending ->
                // Un échec du système ne coupe pas la synchronisation : la prochaine émission réessaie.
                try {
                    if (pending == null) scheduler.cancelAll() else scheduler.replaceAll(pending)
                    AppResult.Success(pending?.size ?: 0)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    AppResult.Failure(AppError.Unknown(e.message))
                }
            }

    /** Rappels à programmer, ou `null` sans session (tout annuler). */
    private fun pendingReminders(todos: List<Todo>, enabled: Boolean, expiresAt: Long?): List<Todo>? {
        if (expiresAt == null) return null
        if (!enabled) return emptyList()
        val currentTime = now()
        return todos.filter { todo -> !todo.isDone && todo.reminderAt.let { it != null && it > currentTime && it < expiresAt } }
    }
}
