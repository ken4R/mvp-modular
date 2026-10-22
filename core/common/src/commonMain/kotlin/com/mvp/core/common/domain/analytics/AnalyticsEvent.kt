package com.mvp.core.common.domain.analytics

/**
 * Événements de mesure d'audience. Liste fermée, sans donnée personnelle : ni texte saisi
 * (titre de tâche, ville, e-mail), ni identifiant de compte. Ajouter un événement = l'ajouter ici,
 * et le décrire dans le texte du consentement Analytics s'il change ce qui est mesuré.
 */
sealed class AnalyticsEvent(val name: String, val properties: Map<String, String> = emptyMap()) {

    /** Un onglet de l'app est affiché. */
    data class ScreenViewed(val screen: AnalyticsScreen) : AnalyticsEvent("screen_viewed", mapOf("screen" to screen.key))

    data object TaskAdded : AnalyticsEvent("task_added")

    data object TaskCompleted : AnalyticsEvent("task_completed")

    /** Envoyé à la main depuis le journal de mesure (builds de debug) pour tester la chaîne. */
    data object DebugTest : AnalyticsEvent("debug_test")
}

/** Écrans mesurés : la clé est ce qui part vers l'outil de mesure (stable, ne pas renommer). */
enum class AnalyticsScreen(val key: String) {
    Tasks("tasks"),
    Weather("weather"),
    Catalog("catalog"),
    Settings("settings"),
}
