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

    fun colors(darkTheme: Boolean): ColorTokens = if (darkTheme) DarkColors else LightColors
}
