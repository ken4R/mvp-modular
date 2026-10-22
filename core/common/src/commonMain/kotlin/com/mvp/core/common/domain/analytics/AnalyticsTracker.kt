package com.mvp.core.common.domain.analytics

/**
 * Point d'entrée de la mesure d'audience pour les features (implémenté par `core:analytics`),
 * sans que les features dépendent de l'outil de mesure.
 */
interface AnalyticsTracker {

    /**
     * Enregistre [event] si l'utilisateur a accepté la mesure d'audience, sinon l'ignore.
     * Ne lève jamais d'exception : la mesure ne doit pas faire échouer une action de l'utilisateur.
     */
    suspend fun track(event: AnalyticsEvent)
}
