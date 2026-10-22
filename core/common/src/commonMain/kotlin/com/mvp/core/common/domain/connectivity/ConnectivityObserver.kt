package com.mvp.core.common.domain.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Contrat entre les features qui doivent savoir si l'appareil a accès à internet (synchronisation des
 * tâches, bandeau « Hors ligne ») et `core:connectivity`, qui l'implémente avec l'API système de chaque
 * plateforme (ADR 0012).
 */
interface ConnectivityObserver {

    /**
     * `true` tant que l'appareil a un réseau utilisable vers internet, réémis à chaque changement.
     * Un `true` ne garantit pas qu'un appel aboutira (serveur en panne, portail captif...) : les appels
     * réseau gèrent toujours leurs échecs.
     */
    fun observeIsOnline(): Flow<Boolean>
}
