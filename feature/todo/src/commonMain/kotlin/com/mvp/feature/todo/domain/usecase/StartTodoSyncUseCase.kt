package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.connectivity.ConnectivityObserver
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.SessionExpiry
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.repository.TodoSyncRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/**
 * Synchronisation automatique des tâches (ADR 0012), à collecter pendant toute la vie de l'app. Tant que
 * l'appareil est en ligne et qu'une session est ouverte : une synchronisation au départ (et à chaque retour
 * du réseau), puis une à chaque modification locale. Un échec est retenté après 2 s, 4 s, 8 s… jusqu'à
 * une minute entre deux essais ; hors ligne ou déconnecté, plus rien n'est tenté. Chaque émission donne le
 * résultat d'une tentative (nombre de modifications encore en attente).
 */
class StartTodoSyncUseCase(
    private val sync: TodoSyncRepository,
    private val connectivity: ConnectivityObserver,
    private val session: SessionExpiry,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, Int>(dispatcher) {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun execute(params: NoParams): Flow<AppResult<Int>> =
        combine(connectivity.observeIsOnline(), session.observeExpiresAt()) { online, expiresAt -> online && expiresAt != null }
            .distinctUntilChanged()
            // Une coupure ou une déconnexion annule la tentative en cours et les essais prévus.
            .transformLatest { canSync ->
                if (!canSync) return@transformLatest
                syncUntilDone()
                sync.observeSyncState()
                    .map { it.pendingChanges }
                    .distinctUntilChanged()
                    .filter { it > 0 }
                    .collect { syncUntilDone() }
            }

    /** Synchronise jusqu'à ce que plus rien n'attende, en espaçant les essais après un échec. */
    private suspend fun FlowCollector<AppResult<Int>>.syncUntilDone() {
        var retryDelay = FIRST_RETRY_DELAY_MILLIS
        while (true) {
            val result = sync.sync()
            emit(result)
            when {
                result is AppResult.Success && result.data == 0 -> return
                // Modifiée pendant l'envoi : on renvoie tout de suite.
                result is AppResult.Success -> retryDelay = FIRST_RETRY_DELAY_MILLIS
                else -> {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
                }
            }
        }
    }

    companion object {
        const val FIRST_RETRY_DELAY_MILLIS = 2_000L
        const val MAX_RETRY_DELAY_MILLIS = 60_000L
    }
}
