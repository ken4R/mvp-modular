package com.mvp.core.featureflags.domain.repository

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.featureflags.domain.model.FlagInputs
import kotlinx.coroutines.flow.Flow

interface FeatureFlagRepository {

    /** Build de debug : réglages locaux pris en compte, menu debug disponible. */
    val isDevBuild: Boolean

    /** Valeurs distantes en cache et réglages locaux ; part du cache disque au premier abonnement. */
    fun observeInputs(): Flow<FlagInputs>

    /** Heure (ms) de la dernière réponse distante valide, `null` si aucune. */
    suspend fun lastFetchedAt(): Long?

    /** Récupère le JSON distant et remplace le cache ; en cas d'échec, le cache reste tel quel. */
    suspend fun fetchRemote(): AppResult<Unit>

    /** Réglage local d'un flag ; `null` le retire. */
    suspend fun setOverride(key: String, enabled: Boolean?)

    suspend fun clearOverrides()
}
