package com.mvp.core.analytics.domain.model

/**
 * Événement prêt à partir vers l'outil de mesure.
 * [sessionId] est un identifiant aléatoire, tiré à chaque lancement de l'app et renouvelé au retrait
 * du consentement et à la déconnexion : il n'est relié ni au compte ni à l'appareil.
 */
data class TrackedEvent(
    val id: Long,
    val name: String,
    val properties: Map<String, String>,
    val timestamp: Long,
    val sessionId: String,
)

/** Ce que montre le journal de mesure (builds de debug) : en attente sur l'appareil, et déjà envoyés. */
data class AnalyticsJournal(
    val pending: List<TrackedEvent>,
    val sent: List<TrackedEvent>,
)
