package com.mvp.core.analytics.data

import com.mvp.core.analytics.data.sink.AnalyticsSink
import com.mvp.core.analytics.domain.model.AnalyticsJournal
import com.mvp.core.analytics.domain.model.TrackedEvent
import com.mvp.core.analytics.domain.repository.AnalyticsRepository
import com.mvp.core.common.domain.analytics.AnalyticsConsent
import com.mvp.core.common.domain.analytics.AnalyticsConsentObserver
import com.mvp.core.common.domain.analytics.AnalyticsEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
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
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val maxPending: Int = DEFAULT_MAX_PENDING,
) : AnalyticsRepository, AnalyticsConsentObserver {

    private val pending = MutableStateFlow<List<TrackedEvent>>(emptyList())
    private val enqueuing = Mutex()
    private val sending = Mutex()

    @Volatile
    private var sessionId = newSessionId()

    // Protégé par [enqueuing].
    private var nextId = 0L

    override suspend fun track(event: AnalyticsEvent) {
        try {
            if (!consent.isGranted()) return
            val size = enqueuing.withLock {
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
            if (size >= batchSize) flush()
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

    override suspend fun onAnalyticsConsentRevoked() {
        pending.value = emptyList()
        sessionId = newSessionId()
    }

    /** Déconnexion : rien de la session de mesure ne passe au compte suivant. */
    suspend fun clear() {
        pending.value = emptyList()
        sessionId = newSessionId()
        sink.reset()
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 10
        const val DEFAULT_MAX_PENDING = 100
    }
}
