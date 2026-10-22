package com.mvp.core.deeplink.data

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.core.deeplink.domain.repository.PendingDeepLinkRepository

/**
 * Un lien en attente au moment d'une déconnexion ne passe pas au compte suivant : il est oublié.
 * Un lien reçu déconnecté, lui, survit à la connexion qui suit (qui efface pourtant les données restantes).
 */
internal class DeepLinkUserSessionObserver(private val pending: PendingDeepLinkRepository) : UserSessionObserver {

    // Rien n'est stocké sur l'appareil : rien à effacer à la connexion d'un autre compte.
    override suspend fun clearUserData(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun onLogout() = pending.clear()
}
