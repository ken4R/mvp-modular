package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.connectivity.ConnectivityObserver
import com.mvp.core.common.domain.error.AppError
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.transformLatest

/**
 * Synchronisation automatique des tâches (ADR 0012), à collecter pendant toute la vie de l'app. Tant que
 * l'appareil est en ligne et qu'une session est ouverte (et pas en cours de déconnexion) : une synchronisation au départ (et à chaque retour
 * du réseau), puis une à chaque modification locale. Un échec est retenté après 2 s, 4 s, 8 s… jusqu'à
 * une minute entre deux essais, sauf un refus du serveur (session refusée) : on attend alors la prochaine
 * modification ou le prochain retour du réseau. Hors ligne ou déconnecté, plus rien n'est tenté. Chaque émission donne le
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
        combine(
            connectivity.observeIsOnline(),
            session.observeExpiresAt(),
            sync.observeIsSignedIn(),
        ) { online, expiresAt, signedIn -> online && expiresAt != null && signedIn }
            .distinctUntilChanged()
            // Une coupure ou une déconnexion annule la tentative en cours et les essais prévus.
            .transformLatest { canSync ->
                if (!canSync) return@transformLatest
                syncUntilDone()
                // Ensuite, à chaque modification (la première valeur est l'état laissé par la synchronisation de départ).
                // Pas de distinctUntilChanged : une modification faite juste après une synchronisation peut redonner
                // le même nombre (1 → 0 → 1 vu comme 1 → 1) et doit quand même partir.
                sync.observePendingChanges()
                    .drop(1)
                    .filter { it > 0 }
                    .collect { syncUntilDone() }
            }
            // Erreur inattendue (base illisible...) : la synchronisation automatique reprend au lieu de s'arrêter.
            .retryWhen { _, _ ->
                delay(MAX_RETRY_DELAY_MILLIS)
                true
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
                // Session refusée : réessayer n'y changera rien, la connexion (feature:auth) s'en occupe.
                result is AppResult.Failure && result.error == AppError.Unauthorized -> return
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
