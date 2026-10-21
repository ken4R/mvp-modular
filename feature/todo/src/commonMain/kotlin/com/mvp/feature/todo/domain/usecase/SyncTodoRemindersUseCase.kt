package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.notification.NotificationsPreference
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.reminder.ReminderScheduler
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException

/**
 * Garde les notifications du système alignées sur les tâches : à chaque changement des tâches ou du
 * réglage Notifications, les rappels programmés sont remplacés par ceux des tâches non terminées dont
 * l'heure est à venir, ou par aucun si les notifications sont coupées. À collecter pendant toute la
 * vie de l'app ; chaque émission donne le nombre de rappels programmés.
 */
class SyncTodoRemindersUseCase(
    private val repository: TodoRepository,
    private val notifications: NotificationsPreference,
    private val scheduler: ReminderScheduler,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, Int>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<Int>> =
        combine(repository.observeTodos(), notifications.observeNotificationsEnabled()) { todos, enabled ->
            if (!enabled) return@combine emptyList()
            val currentTime = now()
            todos.filter { todo -> !todo.isDone && todo.reminderAt.let { it != null && it > currentTime } }
        }
            .distinctUntilChanged()
            .map { pending ->
                // Un échec du système ne coupe pas la synchronisation : la prochaine émission réessaie.
                try {
                    scheduler.replaceAll(pending)
                    AppResult.Success(pending.size)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    AppResult.Failure(AppError.Unknown(e.message))
                }
            }
}
