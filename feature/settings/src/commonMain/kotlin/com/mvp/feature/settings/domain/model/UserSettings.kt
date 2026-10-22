package com.mvp.feature.settings.domain.model

/** Profil affiché dans les réglages. L'e-mail est facultatif (chaîne vide si non renseigné). */
data class UserProfile(val displayName: String, val email: String)

/** Consentements demandés à l'utilisateur (RGPD) : chacun est refusé tant qu'il n'est pas accordé. */
enum class ConsentType { Analytics, Marketing, Personalization }

/**
 * Dernier choix de l'utilisateur pour un consentement. [decidedAt] (epoch ms) et [textVersion]
 * (version de [ConsentTexts] affichée au moment du choix) datent ce choix ; tous deux `null` tant
 * que l'utilisateur n'a rien choisi. Un accord donné sur une ancienne version du texte ne vaut plus :
 * il est lu comme jamais choisi, et redemandé.
 */
data class Consent(
    val type: ConsentType,
    val granted: Boolean,
    val decidedAt: Long? = null,
    val textVersion: Int? = null,
)

/** Choix fait dans l'écran Réglages ; la date et la version du texte sont ajoutées par le use case SetConsent. */
data class ConsentChoice(val type: ConsentType, val granted: Boolean)

data class UserSettings(
    val profile: UserProfile,
    val notificationsEnabled: Boolean,
    val consents: List<Consent>,
    /** Face ID / Touch ID / empreinte (ou code de l'appareil) demandé avant d'afficher l'app. */
    val appLockEnabled: Boolean = false,
) {
    companion object {
        /** Réglages au premier lancement : profil vide, notifications coupées, aucun consentement, pas de verrou. */
        fun initial(): UserSettings = UserSettings(
            profile = UserProfile(displayName = "", email = ""),
            notificationsEnabled = false,
            consents = ConsentType.entries.map { Consent(it, granted = false) },
            appLockEnabled = false,
        )
    }
}
