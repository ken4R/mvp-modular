package com.mvp.feature.onboarding.domain.model

/**
 * Étapes de la présentation du premier lancement, dans l'ordre d'affichage. Chaque étape qui touche à une
 * autorisation l'explique avant que le système ne la demande ; toutes peuvent être remises à plus tard.
 *
 * Swift renomme les entrées des enums Kotlin : côté iOS, comparer [name] (« Welcome », « Notifications »...).
 */
enum class OnboardingStep {
    Welcome,

    /** Permission des notifications (rappels des tâches), puis réglage Notifications activé. */
    Notifications,

    /** Permission de la position approximative (météo à ma position). */
    Location,

    /** Verrou Face ID / Touch ID / empreinte (ou code de l'appareil). Seulement si l'appareil le permet. */
    AppLock,

    /** Consentement Mesure d'audience, accepté ou refusé explicitement. */
    Analytics,
}

/** Ce que l'appareil permet, lu par l'app : les étapes impossibles ne sont pas montrées. */
data class OnboardingDevice(
    /** Biométrie ou code configuré sur l'appareil (sans quoi le verrou ne peut pas être activé). */
    val appLockAvailable: Boolean,
)

/** Version de la présentation : l'augmenter (avec une nouvelle étape) la remontre à ceux qui ont vu l'ancienne. */
const val ONBOARDING_VERSION = 1
