package com.mvp.core.common.domain.analytics

/**
 * Contrat entre le consentement « Mesure d'audience » (`feature:settings`) et la mesure
 * (`core:analytics`), sans dépendance entre les deux. Implémenté par la feature qui garde les consentements.
 */
interface AnalyticsConsent {

    /** `true` seulement si l'utilisateur a accepté la version actuelle du texte de consentement. */
    suspend fun isGranted(): Boolean
}

/**
 * Prévenu dès que l'utilisateur retire son consentement à la mesure d'audience, avant que l'action
 * ne lui soit confirmée. Implémenté par `core:analytics` (`bind AnalyticsConsentObserver::class`).
 */
interface AnalyticsConsentObserver {

    /** Abandonner tout ce qui attend d'être envoyé et oublier l'identifiant de session de mesure. */
    suspend fun onAnalyticsConsentRevoked()
}
