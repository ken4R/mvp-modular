package com.mvp.feature.focus.data.session

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.feature.focus.domain.repository.FocusAlarm
import com.mvp.feature.focus.domain.repository.FocusRepository

/** La session de concentration porte le titre d'une tâche : effacée à la déconnexion, avec son alerte de fin. */
internal class FocusUserSessionObserver(
    private val repository: FocusRepository,
    private val alarm: FocusAlarm,
) : UserSessionObserver {

    override suspend fun clearUserData(): AppResult<Unit> = safeCall {
        repository.update { null }
        alarm.cancel()
    }
}
