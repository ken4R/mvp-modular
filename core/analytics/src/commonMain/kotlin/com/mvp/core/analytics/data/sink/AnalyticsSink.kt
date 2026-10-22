package com.mvp.core.analytics.data.sink

import com.mvp.core.analytics.domain.model.TrackedEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Destination des événements : l'outil de mesure. Brancher un vrai outil (Firebase, Amplitude,
 * PostHog, Matomo...) = une nouvelle implémentation déclarée dans `analyticsModule`, rien d'autre ne change.
 */
interface AnalyticsSink {

    /** Envoie un lot ; lève une exception en cas d'échec (le lot reste alors en attente). */
    suspend fun send(batch: List<TrackedEvent>)

    /** Oublie ce que l'outil garde sur l'appareil (identifiants, cache) : appelé à la déconnexion. */
    suspend fun reset()
}

/**
 * Pas encore d'outil de mesure : les lots « envoyés » restent sur l'appareil, en mémoire, pour le
 * journal de debug. Rien ne quitte l'appareil et rien n'est écrit sur le disque.
 */
internal class DebugAnalyticsSink(private val capacity: Int = DEFAULT_CAPACITY) : AnalyticsSink {

    private val received = MutableStateFlow<List<TrackedEvent>>(emptyList())

    /** Derniers événements reçus, du plus récent au plus ancien. */
    fun observeSent(): Flow<List<TrackedEvent>> = received.asStateFlow()

    override suspend fun send(batch: List<TrackedEvent>) = received.update { (batch.reversed() + it).take(capacity) }

    override suspend fun reset() {
        received.value = emptyList()
    }

    companion object {
        const val DEFAULT_CAPACITY = 50
    }
}
