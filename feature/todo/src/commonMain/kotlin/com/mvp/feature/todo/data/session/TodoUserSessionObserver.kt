package com.mvp.feature.todo.data.session

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.feature.todo.data.source.TodoLocalDataSource
import com.mvp.feature.todo.domain.reminder.ReminderScheduler

/**
 * Les tâches appartiennent à l'utilisateur connecté : elles sont effacées à la déconnexion, et leurs
 * rappels annulés (y compris ceux déjà affichés, qui montrent le titre des tâches).
 */
internal class TodoUserSessionObserver(
    private val local: TodoLocalDataSource,
    private val reminders: ReminderScheduler,
) : UserSessionObserver {
    override suspend fun clearUserData(): AppResult<Unit> = safeCall {
        local.clear()
        reminders.cancelAll()
    }
}
