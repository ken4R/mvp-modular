package com.mvp.feature.todo.data.sync

import com.mvp.core.common.domain.connectivity.ConnectivityObserver
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Faux serveur, en mémoire, en attendant un backend : il applique la règle du vrai (la version la plus
 * récente gagne, les suppressions sont gardées) et ne répond pas quand l'appareil est hors ligne, pour
 * qu'on puisse essayer le mode hors ligne. Il n'a qu'un compte : il est vidé à la déconnexion, pour qu'un
 * autre compte ne récupère pas les tâches du précédent. Ses données sont perdues à la fermeture de l'app ;
 * la base locale, elle, garde tout.
 */
internal class FakeTodoRemoteDataSource(
    private val connectivity: ConnectivityObserver,
    private val latencyMillis: Long = SIMULATED_LATENCY_MILLIS,
) : TodoRemoteDataSource {

    private val mutex = Mutex()
    private val records = mutableMapOf<String, TodoRecord>()

    override suspend fun push(changes: List<TodoRecord>): AppResult<Unit> = respond {
        changes.forEach { change ->
            val current = records[change.id]
            // À égalité, la modification reçue est la même (renvoi après une coupure) : on la garde.
            if (current == null || change.updatedAt >= current.updatedAt) records[change.id] = change
        }
    }

    override suspend fun fetchAll(): AppResult<List<TodoRecord>> = respond { records.values.toList() }

    override suspend fun signOut() = mutex.withLock { records.clear() }

    private suspend fun <T> respond(block: () -> T): AppResult<T> {
        delay(latencyMillis)
        if (!connectivity.observeIsOnline().first()) return AppResult.Failure(AppError.Network)
        return mutex.withLock { AppResult.Success(block()) }
    }

    private companion object {
        /** Délai d'un aller-retour, pour voir passer « Synchronisation… » à l'écran. */
        const val SIMULATED_LATENCY_MILLIS = 400L
    }
}
