package com.mvp.feature.todo.data.sync

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.todo.domain.model.TodoSyncState
import com.mvp.feature.todo.domain.repository.TodoSyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Envoie la file d'attente locale, puis applique ce que le serveur a de plus récent (ADR 0012). Le verrou
 * sérialise les synchronisations et la déconnexion : une synchronisation ne peut pas réécrire les tâches
 * d'un compte après leur effacement.
 */
internal class TodoSyncRepositoryImpl(
    private val local: TodoSyncLocalDataSource,
    private val remote: TodoRemoteDataSource,
) : TodoSyncRepository {

    private val mutex = Mutex()
    private val progress = MutableStateFlow(Progress())

    /** Entre la déconnexion et la connexion suivante, plus rien ne part ni n'arrive. */
    private var signedOut = false

    private data class Progress(val isSyncing: Boolean = false, val lastSyncFailed: Boolean = false)

    override fun observeSyncState(): Flow<TodoSyncState> =
        combine(local.observePendingCount(), progress) { pendingChanges, current ->
            TodoSyncState(pendingChanges, current.isSyncing, current.lastSyncFailed)
        }.distinctUntilChanged()

    override suspend fun sync(): AppResult<Int> = mutex.withLock {
        if (signedOut) return@withLock AppResult.Failure(AppError.Unauthorized)
        progress.update { it.copy(isSyncing = true) }
        val result = try {
            pushThenPull()
        } catch (e: CancellationException) {
            progress.update { it.copy(isSyncing = false) }
            throw e
        } catch (e: Throwable) {
            AppResult.Failure(AppError.Unknown(e.message))
        }
        progress.value = Progress(isSyncing = false, lastSyncFailed = result is AppResult.Failure)
        result
    }

    private suspend fun pushThenPull(): AppResult<Int> {
        val changes = local.pendingChanges()
        if (changes.isNotEmpty()) {
            val pushed = remote.push(changes)
            if (pushed is AppResult.Failure) return pushed
            local.markSent(changes)
        }
        val records = when (val fetched = remote.fetchAll()) {
            is AppResult.Failure -> return fetched
            is AppResult.Success -> fetched.data
        }
        local.applyRemote(records)
        return AppResult.Success(local.pendingChanges().size)
    }

    /**
     * Déconnexion : attend la fin d'une synchronisation en cours, prévient le serveur, puis lance [clear]
     * (effacement des données locales). Jusqu'à [onSignedIn], [sync] échoue sans rien envoyer.
     */
    suspend fun signOut(clear: suspend () -> Unit) = mutex.withLock {
        signedOut = true
        remote.signOut()
        clear()
        progress.value = Progress()
    }

    suspend fun onSignedIn() = mutex.withLock { signedOut = false }
}
