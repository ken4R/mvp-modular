package com.mvp.feature.todo.data.sync

import com.mvp.core.common.domain.result.AppResult

/**
 * Serveur des tâches du compte connecté (ADR 0012). Pas encore de backend : l'app utilise
 * [FakeTodoRemoteDataSource]. Un vrai serveur sera une autre implémentation (Ktor, `safeApiCall`), qui applique
 * la même règle : pour chaque tâche, la version la plus récente gagne.
 */
interface TodoRemoteDataSource {

    /**
     * Envoie les modifications. Le serveur garde, pour chaque tâche, la plus récente entre la sienne et
     * celle reçue ; renvoyer deux fois la même modification ne change rien. Échoue avec
     * [com.mvp.core.common.domain.error.AppError.Network] sans réseau.
     */
    suspend fun push(changes: List<TodoRecord>): AppResult<Unit>

    /** Toutes les tâches du compte, suppressions comprises. */
    suspend fun fetchAll(): AppResult<List<TodoRecord>>

    /** Déconnexion : oublier ce qui est lié au compte (jeton, ou les tâches pour le faux serveur). */
    suspend fun signOut()
}
