package com.mvp.feature.onboarding.domain.model

/**
 * Texte d'une étape. [declineLabel] est `null` quand il n'y a rien à refuser (accueil).
 * Le consentement Mesure d'audience est montré avec son propre texte versionné (ConsentTexts, feature:settings) :
 * l'accord porte sur ce texte-là.
 */
data class OnboardingText(
    val title: String,
    val body: String,
    val acceptLabel: String,
    val declineLabel: String?,
)

/** Textes de la présentation, communs aux deux apps. */
object OnboardingTexts {

    /** Termine la présentation tout de suite, depuis n'importe quelle étape. */
    val skipLabel = "Passer"

    fun of(step: OnboardingStep): OnboardingText = when (step) {
        OnboardingStep.Welcome -> OnboardingText(
            title = "Bienvenue",
            body = "Vos tâches, la météo et un catalogue, sur Android et iOS. Avant de commencer, voici ce que l'app " +
                "peut vous demander et à quoi ça sert. Rien n'est obligatoire, et tout se change ensuite dans Réglages.",
            acceptLabel = "Commencer",
            declineLabel = null,
        )
        OnboardingStep.Notifications -> OnboardingText(
            title = "Rappels des tâches",
            body = "Les notifications servent uniquement à vous prévenir à l'heure du rappel que vous fixez sur une " +
                "tâche. Pas de publicité, rien sans rappel.",
            acceptLabel = "Autoriser les notifications",
            declineLabel = "Plus tard",
        )
        OnboardingStep.Location -> OnboardingText(
            title = "Météo à votre position",
            body = "Votre position approximative (environ 1 km) est envoyée au service météo Open-Meteo, seulement " +
                "quand vous demandez la météo à votre position. Elle n'est pas enregistrée. Sans elle, vous pouvez " +
                "toujours chercher une ville.",
            acceptLabel = "Autoriser la position",
            declineLabel = "Plus tard",
        )
        OnboardingStep.AppLock -> OnboardingText(
            title = "Verrouiller l'app",
            body = "Demander Face ID, Touch ID, votre empreinte ou le code de l'appareil à l'ouverture de l'app et à " +
                "chaque retour. La vérification est faite par le système : l'app ne voit jamais vos données biométriques.",
            acceptLabel = "Activer le verrou",
            declineLabel = "Plus tard",
        )
        OnboardingStep.Analytics -> OnboardingText(
            title = "Mesure d'audience",
            body = "Nous aider à améliorer l'app avec des statistiques d'usage ? Refuser ne change rien à ce que vous " +
                "pouvez faire. Vous pourrez changer d'avis dans Réglages > Consentements.",
            acceptLabel = "Accepter",
            declineLabel = "Refuser",
        )
    }
}
