package com.mvp.feature.settings.domain.model

import com.mvp.core.designsystem.Appearance

/** Profil affiché dans les réglages. L'e-mail est facultatif (chaîne vide si non renseigné). */
data class UserProfile(val displayName: String, val email: String)

/** Consentements demandés à l'utilisateur (RGPD) : chacun est refusé tant qu'il n'est pas accordé. */
enum class ConsentType {
    Analytics,

    /** Rapports de plantage (ADR 0020), séparé de la mesure d'audience : l'un peut être accepté sans l'autre. */
    CrashReports,
    Marketing,
    Personalization,
}

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
    /** Thème et couleur d'accent : préférence de l'appareil, gardée à la déconnexion (ADR 0015). */
    val appearance: Appearance = Appearance.Default,
) {
    companion object {
        /**
         * Réglages au premier lancement : profil vide, notifications coupées, aucun consentement, pas de verrou,
         * thème de l'appareil et accent bleu.
         */
        fun initial(): UserSettings = UserSettings(
            profile = UserProfile(displayName = "", email = ""),
            notificationsEnabled = false,
            consents = ConsentType.entries.map { Consent(it, granted = false) },
            appLockEnabled = false,
            appearance = Appearance.Default,
        )
    }
}
