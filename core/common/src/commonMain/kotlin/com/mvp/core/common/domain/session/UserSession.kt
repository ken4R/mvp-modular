package com.mvp.core.common.domain.session

import com.mvp.core.common.domain.result.AppResult

/** Compte connecté, tel que les features le voient (sans jeton ni secret). */
data class UserAccount(val id: String, val displayName: String, val email: String)

/**
 * Contrat entre la connexion (`feature:auth`) et les features qui gardent des données de
 * l'utilisateur, sans que les features dépendent les unes des autres. Chaque feature concernée
 * déclare son implémentation dans son module Koin (`bind UserSessionObserver::class`) ;
 * les use cases de connexion et de déconnexion les reçoivent toutes.
 */
interface UserSessionObserver {

    /** Après une connexion réussie : par exemple pré-remplir le profil à partir du compte. */
    suspend fun onLogin(account: UserAccount): AppResult<Unit> = AppResult.Success(Unit)

    /** À la déconnexion (ou au changement de compte) : effacer toutes les données locales de l'utilisateur. */
    suspend fun clearUserData(): AppResult<Unit>

    /**
     * Après une déconnexion explicite réussie (pas à la connexion d'un autre compte) : oublier ce qui
     * ne vaut que pour la session en cours, par exemple un lien profond en attente.
     */
    suspend fun onLogout() {}
}
