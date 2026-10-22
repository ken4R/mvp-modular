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
