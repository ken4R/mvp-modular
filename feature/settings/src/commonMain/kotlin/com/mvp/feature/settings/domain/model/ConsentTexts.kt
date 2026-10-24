package com.mvp.feature.settings.domain.model

/** Texte d'un consentement tel qu'il est montré à l'utilisateur. */
data class ConsentText(val title: String, val summary: String, val version: Int)

/**
 * Textes des consentements, communs aux deux apps : l'accord donné porte sur ce texte précis.
 * Changer ce qui est fait des données (nouvel événement mesuré, nouvel outil) = modifier le texte
 * ET augmenter sa version : les accords donnés sur l'ancienne version ne valent plus et sont redemandés.
 */
object ConsentTexts {

    fun of(type: ConsentType): ConsentText = when (type) {
        ConsentType.Analytics -> ConsentText(
            title = "Mesure d'audience",
            summary = "Statistiques d'usage (onglets ouverts, tâches ajoutées ou terminées) pour améliorer l'app, " +
                "liées à un identifiant aléatoire renouvelé à chaque lancement. Jamais le contenu de vos tâches, " +
                "ni votre nom, ni votre e-mail.",
            version = 1,
        )
        ConsentType.CrashReports -> ConsentText(
            title = "Rapports de plantage",
            summary = "Si l'app plante, un rapport est envoyé au lancement suivant pour corriger le problème : " +
                "le type d'erreur, l'endroit du code concerné, la version du système et l'heure. Jamais le contenu " +
                "de vos tâches ni de vos notes, ni votre nom, ni votre e-mail.",
            version = 1,
        )
        ConsentType.Marketing -> ConsentText(
            title = "Communications marketing",
            summary = "Offres et nouveautés par e-mail ou notification",
            version = 1,
        )
        ConsentType.Personalization -> ConsentText(
            title = "Personnalisation",
            summary = "Contenus adaptés à votre utilisation",
            version = 1,
        )
    }
}
