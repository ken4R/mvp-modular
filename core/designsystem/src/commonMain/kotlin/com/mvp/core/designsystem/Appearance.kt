package com.mvp.core.designsystem

/** Thème choisi dans Réglages : suivre l'appareil, ou forcer le clair ou le sombre. */
enum class ThemeMode { System, Light, Dark }

/**
 * Couleur d'accent choisie dans Réglages : elle remplace la couleur principale (boutons, titres de section,
 * interrupteurs) et le fond de mise en évidence, en clair comme en sombre. Le reste de la palette ne change pas.
 */
enum class AccentColor { Blue, Teal, Violet, Orange, Pink }

/** Apparence de l'app : thème et couleur d'accent. Valeur par défaut : thème de l'appareil, accent bleu. */
data class Appearance(
    val themeMode: ThemeMode = ThemeMode.System,
    val accent: AccentColor = AccentColor.Blue,
) {
    /** Thème sombre à appliquer, [systemDark] étant le thème actuel de l'appareil. */
    fun isDark(systemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    companion object {
        /** Pour Swift, qui n'a pas accès aux valeurs par défaut du constructeur. */
        val Default = Appearance()
    }
}
