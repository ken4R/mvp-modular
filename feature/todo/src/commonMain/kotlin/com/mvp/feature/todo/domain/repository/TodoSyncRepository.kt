package com.mvp.feature.todo.domain.repository

import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.todo.domain.model.TodoSyncState
import kotlinx.coroutines.flow.Flow

/** Synchronisation des tâches avec le serveur du compte (ADR 0012). */
interface TodoSyncRepository {

    fun observeSyncState(): Flow<TodoSyncState>

    /** Nombre de modifications en attente, réémis à **chaque** écriture dans la file (même nombre compris). */
    fun observePendingChanges(): Flow<Int>

    /**
     * `false` entre une déconnexion et la connexion suivante : rien ne doit se synchroniser, même si la session
     * est encore lisible (elle n'est effacée qu'après les données des features).
     */
    fun observeIsSignedIn(): Flow<Boolean>

    /**
     * Envoie les modifications en attente puis récupère celles du serveur. Une seule synchronisation à la
     * fois : un second appel attend la fin du premier. Renvoie le nombre de modifications encore en attente
     * (faites pendant l'envoi) ; échoue avec [com.mvp.core.common.domain.error.AppError.Network] sans réseau.
     */
    suspend fun sync(): AppResult<Int>
}
