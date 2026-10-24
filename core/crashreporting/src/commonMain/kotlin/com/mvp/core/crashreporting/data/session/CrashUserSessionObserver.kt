package com.mvp.core.crashreporting.data.session

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.core.crashreporting.data.CrashReporter

/** À la déconnexion (ou au changement de compte) : rapports en attente et journal effacés. */
internal class CrashUserSessionObserver(private val reporter: CrashReporter) : UserSessionObserver {

    override suspend fun clearUserData(): AppResult<Unit> = safeCall { reporter.clear() }
}
