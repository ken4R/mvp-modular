package com.mvp.core.deeplink.data

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.core.deeplink.domain.repository.PendingDeepLinkRepository

/** Un lien reçu pendant la session ne passe pas au compte suivant : il est oublié à la déconnexion. */
internal class DeepLinkUserSessionObserver(private val pending: PendingDeepLinkRepository) : UserSessionObserver {
    override suspend fun clearUserData(): AppResult<Unit> {
        pending.clear()
        return AppResult.Success(Unit)
    }
}
