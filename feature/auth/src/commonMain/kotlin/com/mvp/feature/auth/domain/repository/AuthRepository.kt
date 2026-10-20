package com.mvp.feature.auth.domain.repository

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.feature.auth.domain.model.Credentials
import com.mvp.feature.auth.domain.model.Session
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Session valide en cours, ou `null` (jamais connecté, déconnecté ou session expirée). */
    fun observeSession(): Flow<Session?>

    /** Dernier compte connecté sur l'appareil, même si sa session a expiré. */
    suspend fun lastAccount(): AppResult<UserAccount?>

    /** Vérifie les identifiants auprès du service d'authentification, sans rien enregistrer. */
    suspend fun authenticate(credentials: Credentials): AppResult<Session>

    suspend fun saveSession(session: Session): AppResult<Unit>
    suspend fun clearSession(): AppResult<Unit>
}
