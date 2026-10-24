package com.mvp.core.common.domain.crash

import kotlinx.coroutines.flow.Flow

/**
 * Contrat entre le consentement « Rapports de plantage » (`feature:settings`) et le suivi des plantages
 * (`core:crashreporting`), sans dépendance entre les deux. Distinct du consentement Mesure d'audience (ADR 0020).
 */
interface CrashReportingConsent {

    /** `true` seulement si l'utilisateur a accepté la version actuelle du texte ; réémis à chaque changement. */
    fun observeGranted(): Flow<Boolean>
}

/**
 * Prévenu dès que l'utilisateur retire son consentement aux rapports de plantage, avant que l'action ne lui soit
 * confirmée. Implémenté par `core:crashreporting` (`bind CrashReportingConsentObserver::class`).
 */
interface CrashReportingConsentObserver {

    /** Arrêter l'enregistrement des plantages et effacer les rapports qui attendent d'être envoyés. */
    suspend fun onCrashReportingConsentRevoked()
}
