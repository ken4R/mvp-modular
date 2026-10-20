package com.mvp.feature.settings.domain.model

/** Profil affiché dans les réglages. L'e-mail est facultatif (chaîne vide si non renseigné). */
data class UserProfile(val displayName: String, val email: String)

/** Consentements demandés à l'utilisateur (RGPD) : chacun est refusé tant qu'il n'est pas accordé. */
enum class ConsentType { Analytics, Marketing, Personalization }

data class Consent(val type: ConsentType, val granted: Boolean)

data class UserSettings(
    val profile: UserProfile,
    val notificationsEnabled: Boolean,
    val consents: List<Consent>,
) {
    companion object {
        /** Réglages au premier lancement : profil vide, notifications coupées, aucun consentement. */
        fun initial(): UserSettings = UserSettings(
            profile = UserProfile(displayName = "", email = ""),
            notificationsEnabled = false,
            consents = ConsentType.entries.map { Consent(it, granted = false) },
        )
    }
}
