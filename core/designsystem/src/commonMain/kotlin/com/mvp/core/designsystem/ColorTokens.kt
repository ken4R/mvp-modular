package com.mvp.core.designsystem

/**
 * Palette sémantique partagée Android / iOS.
 * Couleurs en ARGB (0xAARRGGBB) : chaque plateforme les convertit dans son type natif
 * (androidx.compose.ui.graphics.Color, SwiftUI.Color).
 */
data class ColorTokens(
    val primary: Long,
    val onPrimary: Long,
    val secondary: Long,
    val onSecondary: Long,
    val background: Long,
    val onBackground: Long,
    val surface: Long,
    val onSurface: Long,
    val error: Long,
    val onError: Long,
    /** Fond d'un élément mis en évidence (par exemple la tâche ouverte par un lien), texte en [onSurface]. */
    val highlight: Long,
)

internal object Palette {
    const val Blue600 = 0xFF1E5EFFL
    const val Blue300 = 0xFF8AAEFFL
    const val Blue50 = 0xFFE6EEFFL
    const val Blue900 = 0xFF1C2B4DL
    const val Teal700 = 0xFF00796BL
    const val Teal50 = 0xFFDDF2EFL
    const val Teal900 = 0xFF12383AL
    const val Violet600 = 0xFF6A3FD8L
    const val Violet300 = 0xFFB39DFFL
    const val Violet50 = 0xFFEEE8FFL
    const val Violet900 = 0xFF2D2350L
    const val Orange700 = 0xFFB8470BL
    const val Orange300 = 0xFFFFB27AL
    const val Orange50 = 0xFFFFEBDCL
    const val Orange900 = 0xFF472A16L
    const val Pink700 = 0xFFC2185BL
    const val Pink300 = 0xFFF48FB1L
    const val Pink50 = 0xFFFCE4ECL
    const val Pink900 = 0xFF4A1B2EL
    const val Teal600 = 0xFF00897BL
    const val Teal300 = 0xFF4DB6ACL
    const val Red600 = 0xFFD32F2FL
    const val Red300 = 0xFFEF9A9AL
    const val White = 0xFFFFFFFFL
    const val Grey50 = 0xFFF7F8FAL
    const val Grey900 = 0xFF15171AL
    const val Grey800 = 0xFF22252AL
    const val Black = 0xFF000000L
}

val LightColors = ColorTokens(
    primary = Palette.Blue600,
    onPrimary = Palette.White,
    secondary = Palette.Teal600,
    onSecondary = Palette.White,
    background = Palette.Grey50,
    onBackground = Palette.Grey900,
    surface = Palette.White,
    onSurface = Palette.Grey900,
    error = Palette.Red600,
    onError = Palette.White,
    highlight = Palette.Blue50,
)

val DarkColors = ColorTokens(
    primary = Palette.Blue300,
    onPrimary = Palette.Black,
    secondary = Palette.Teal300,
    onSecondary = Palette.Black,
    background = Palette.Grey900,
    onBackground = Palette.Grey50,
    surface = Palette.Grey800,
    onSurface = Palette.Grey50,
    error = Palette.Red300,
    onError = Palette.Black,
    highlight = Palette.Blue900,
)

/**
 * Couleurs qui changent avec l'accent. Contrastes vérifiés par DesignSystemTest (WCAG AA, 4,5:1) :
 * [primary] contre [onPrimary] et contre le fond, [highlight] contre le texte `onSurface`.
 */
internal data class AccentTokens(val primary: Long, val onPrimary: Long, val highlight: Long)

internal fun accentTokens(accent: AccentColor, darkTheme: Boolean): AccentTokens = if (darkTheme) {
    when (accent) {
        AccentColor.Blue -> AccentTokens(Palette.Blue300, Palette.Black, Palette.Blue900)
        AccentColor.Teal -> AccentTokens(Palette.Teal300, Palette.Black, Palette.Teal900)
        AccentColor.Violet -> AccentTokens(Palette.Violet300, Palette.Black, Palette.Violet900)
        AccentColor.Orange -> AccentTokens(Palette.Orange300, Palette.Black, Palette.Orange900)
        AccentColor.Pink -> AccentTokens(Palette.Pink300, Palette.Black, Palette.Pink900)
    }
} else {
    when (accent) {
        AccentColor.Blue -> AccentTokens(Palette.Blue600, Palette.White, Palette.Blue50)
        AccentColor.Teal -> AccentTokens(Palette.Teal700, Palette.White, Palette.Teal50)
        AccentColor.Violet -> AccentTokens(Palette.Violet600, Palette.White, Palette.Violet50)
        AccentColor.Orange -> AccentTokens(Palette.Orange700, Palette.White, Palette.Orange50)
        AccentColor.Pink -> AccentTokens(Palette.Pink700, Palette.White, Palette.Pink50)
    }
}
