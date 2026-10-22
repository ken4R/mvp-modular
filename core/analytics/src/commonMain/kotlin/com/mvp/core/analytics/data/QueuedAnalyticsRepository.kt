package com.mvp.core.analytics.data

import com.mvp.core.analytics.data.sink.AnalyticsSink
import com.mvp.core.analytics.domain.model.AnalyticsJournal
import com.mvp.core.analytics.domain.model.TrackedEvent
import com.mvp.core.analytics.domain.repository.AnalyticsRepository
import com.mvp.core.common.domain.analytics.AnalyticsConsent
import com.mvp.core.common.domain.analytics.AnalyticsConsentObserver
import com.mvp.core.common.domain.analytics.AnalyticsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * File d'attente en mémoire seulement (perdue si l'app est tuée : rien de la mesure n'est écrit sur le disque),
 * envoyée au [sink] par lots de [batchSize], ou quand l'app passe en arrière-plan.
 *
 * Le consentement est relu à chaque événement et avant chaque envoi : sans consentement valide
 * (refusé, retiré, ou donné sur une ancienne version du texte), rien n'est gardé ni envoyé.
 */
internal class QueuedAnalyticsRepository(
    private val consent: AnalyticsConsent,
    private val sink: AnalyticsSink,
    private val sentEvents: Flow<List<TrackedEvent>>,
    private val now: () -> Long,
    private val newSessionId: () -> String,
    /** Portée de l'app : l'envoi d'un lot plein part sans faire attendre l'action de l'utilisateur. */
    private val scope: CoroutineScope,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val maxPending: Int = DEFAULT_MAX_PENDING,
) : AnalyticsRepository, AnalyticsConsentObserver {

    private val pending = MutableStateFlow<List<TrackedEvent>>(emptyList())
    private val enqueuing = Mutex()
    private val sending = Mutex()

    @Volatile
    private var sessionId = newSessionId()

    /**
     * Augmente à chaque retrait du consentement et à chaque déconnexion (sous [enqueuing]) : un événement
     * dont le consentement a été lu avant n'entre pas dans la file, même s'il arrive juste après.
     */
    @Volatile
    private var epoch = 0L

    // Protégé par [enqueuing].
    private var nextId = 0L

    override suspend fun track(event: AnalyticsEvent) {
        try {
            val readAt = epoch
            if (!consent.isGranted()) return
            val size = enqueuing.withLock {
                if (epoch != readAt) return
                val tracked = TrackedEvent(
                    id = nextId++,
                    name = event.name,
                    properties = event.properties,
                    timestamp = now(),
                    sessionId = sessionId,
                )
                // File bornée : au-delà, les plus anciens sont abandonnés (outil injoignable longtemps).
                pending.updateAndGet { (it + tracked).takeLast(maxPending) }.size
            }
            if (size >= batchSize) scope.launch { runCatchingNonCancellation { flush() } }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // La mesure ne fait jamais échouer l'action de l'utilisateur.
        }
    }

    override suspend fun flush(): Int = sending.withLock {
        if (!consent.isGranted()) {
            pending.value = emptyList()
            return 0
        }
        val batch = pending.value
        if (batch.isEmpty()) return 0
        sink.send(batch)
        // Seuls les événements envoyés quittent la file : ceux arrivés pendant l'envoi attendent le suivant.
        val sentIds = batch.mapTo(HashSet()) { it.id }
        pending.update { queue -> queue.filterNot { it.id in sentIds } }
        batch.size
    }

    override fun observeJournal(): Flow<AnalyticsJournal> =
        combine(pending, sentEvents) { pending, sent -> AnalyticsJournal(pending = pending.reversed(), sent = sent) }

    /** Retrait : file abandonnée, nouvelle session, et l'outil oublie ce qu'il garde sur l'appareil. */
    override suspend fun onAnalyticsConsentRevoked() = forget()

    /** Déconnexion : rien de la session de mesure ne passe au compte suivant. */
    suspend fun clear() = forget()

    private suspend fun forget() {
        enqueuing.withLock {
            epoch++
            pending.value = emptyList()
            sessionId = newSessionId()
        }
        // Attend la fin d'un envoi déjà commencé : le retrait n'est confirmé qu'une fois plus rien en cours.
        // Un échec de l'outil ne fait pas échouer le retrait : la file est déjà vidée.
        sending.withLock { runCatchingNonCancellation { sink.reset() } }
    }

    private suspend fun runCatchingNonCancellation(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Gardé pour le prochain envoi (lot) ou sans effet (remise à zéro de l'outil).
        }
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 10
        const val DEFAULT_MAX_PENDING = 100
    }
}
