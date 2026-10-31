package com.mvp.feature.focus.domain.repository

import com.mvp.feature.focus.domain.model.FocusSession
import kotlinx.coroutines.flow.Flow

/** Session en cours, gardée sur l'appareil : elle survit à l'arrêt de l'app (action depuis la notification, relance). */
interface FocusRepository {
    fun observe(): Flow<FocusSession?>

    /**
     * Remplace la session par le résultat de [transform] (`null` : plus de session), lecture et écriture d'un seul
     * tenant : l'écran et une action de la notification ne s'écrasent pas. Renvoie la nouvelle session.
     */
    suspend fun update(transform: (FocusSession?) -> FocusSession?): FocusSession?
}

/** Alerte de fin de phase (notification locale programmée), qui part même app fermée. */
interface FocusAlarm {
    /** Programme l'alerte de fin de [session] (en cours), à la place de la précédente. Rien si le réglage Notifications est coupé. */
    suspend fun schedule(session: FocusSession)

    suspend fun cancel()
}
