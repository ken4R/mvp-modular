package com.mvp.feature.todo.data.session

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.feature.todo.data.source.TodoLocalDataSource
import com.mvp.feature.todo.data.sync.TodoSyncRepositoryImpl
import com.mvp.feature.todo.domain.reminder.LocationReminderScheduler
import com.mvp.feature.todo.domain.reminder.ReminderScheduler

/**
 * Les tâches appartiennent à l'utilisateur connecté : elles sont effacées à la déconnexion avec les
 * modifications pas encore envoyées (lieux des rappels par lieu compris, ADR 0023), et leurs rappels annulés, à
 * l'heure et par lieu (y compris ceux déjà affichés, qui montrent le titre des tâches). L'effacement attend la fin d'une synchronisation en cours, qui ne peut donc pas les
 * réécrire ; plus rien ne se synchronise jusqu'à la connexion suivante.
 */
internal class TodoUserSessionObserver(
    private val local: TodoLocalDataSource,
    private val reminders: ReminderScheduler,
    private val locationReminders: LocationReminderScheduler,
    private val sync: TodoSyncRepositoryImpl,
) : UserSessionObserver {

    override suspend fun onLogin(account: UserAccount): AppResult<Unit> {
        sync.onSignedIn()
        return AppResult.Success(Unit)
    }

    override suspend fun clearUserData(): AppResult<Unit> = safeCall {
        sync.signOut {
            local.clear()
            reminders.cancelAll()
            locationReminders.cancelAll()
        }
    }
}
