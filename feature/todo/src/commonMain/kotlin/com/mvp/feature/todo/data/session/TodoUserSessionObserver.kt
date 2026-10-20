package com.mvp.feature.todo.data.session

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.feature.todo.data.source.TodoLocalDataSource

/** Les tâches appartiennent à l'utilisateur connecté : elles sont effacées à la déconnexion. */
internal class TodoUserSessionObserver(private val local: TodoLocalDataSource) : UserSessionObserver {
    override suspend fun clearUserData(): AppResult<Unit> = safeCall { local.clear() }
}
