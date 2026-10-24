package com.mvp.core.designsystem

/**
 * Point d'entrée unique du design system, consommé par l'UI Android (Compose)
 * et iOS (SwiftUI). Les apps ne définissent aucune valeur visuelle en dur :
 * elles lisent tout ici, ce qui garde les deux plateformes alignées.
 */
object DesignSystem {
    val spacing = SpacingTokens()
    val radius = RadiusTokens()
    val sizes = SizeTokens()
    val typography = TypographyTokens()

    /** Palette par défaut (accent bleu) : widgets, et écrans affichés avant que l'apparence ne soit lue. */
    fun colors(darkTheme: Boolean): ColorTokens = colors(darkTheme, AccentColor.Blue)

    /** Palette claire ou sombre avec la couleur d'accent choisie dans Réglages. */
    fun colors(darkTheme: Boolean, accent: AccentColor): ColorTokens {
        val base = if (darkTheme) DarkColors else LightColors
        val tokens = accentTokens(accent, darkTheme)
        return base.copy(primary = tokens.primary, onPrimary = tokens.onPrimary, highlight = tokens.highlight)
    }

    /** Palette de l'[appearance] choisie, [systemDark] étant le thème actuel de l'appareil. */
    fun colors(appearance: Appearance, systemDark: Boolean): ColorTokens =
        colors(appearance.isDark(systemDark), appearance.accent)
}
