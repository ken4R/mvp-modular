package com.mvp.core.analytics.data.session

import com.mvp.core.analytics.data.QueuedAnalyticsRepository
import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserSessionObserver

/** À la déconnexion (ou au changement de compte) : file d'attente, journal et identifiant de session effacés. */
internal class AnalyticsUserSessionObserver(private val repository: QueuedAnalyticsRepository) : UserSessionObserver {

    override suspend fun clearUserData(): AppResult<Unit> = safeCall { repository.clear() }
}
