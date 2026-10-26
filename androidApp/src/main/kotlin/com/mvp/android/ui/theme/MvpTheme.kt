package com.mvp.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mvp.core.designsystem.AccentColor
import com.mvp.core.designsystem.Appearance
import com.mvp.core.designsystem.ColorTokens
import com.mvp.core.designsystem.DesignSystem
import com.mvp.core.designsystem.TextStyleToken

/** Thème appliqué (sombre ou non) et palette complète, pour ce que MaterialTheme ne porte pas. */
private data class MvpThemeState(val dark: Boolean, val colors: ColorTokens)

private val LocalMvpTheme = staticCompositionLocalOf { MvpThemeState(dark = false, colors = DesignSystem.colors(darkTheme = false)) }

/**
 * Thème Compose construit uniquement à partir des tokens du module core:designsystem, selon l'[appearance]
 * choisie dans Réglages (thème de l'appareil, clair ou sombre, et couleur d'accent, ADR 0015).
 */
@Composable
fun MvpTheme(appearance: Appearance = Appearance.Default, content: @Composable () -> Unit) {
    val dark = appearance.isDark(isSystemInDarkTheme())
    val colors = DesignSystem.colors(dark, appearance.accent)
    CompositionLocalProvider(LocalMvpTheme provides MvpThemeState(dark, colors)) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(dark),
            typography = DesignSystem.typography.let {
                Typography(
                    headlineSmall = it.titleLarge.toTextStyle(),
                    titleMedium = it.titleMedium.toTextStyle(),
                    bodyLarge = it.body.toTextStyle(),
                    labelLarge = it.label.toTextStyle(),
                    bodySmall = it.caption.toTextStyle(),
                )
            },
            content = content,
        )
    }
}

/** Accès aux espacements en dp : `Spacing.md`. */
object Spacing {
    val xs = DesignSystem.spacing.xs.dp
    val sm = DesignSystem.spacing.sm.dp
    val md = DesignSystem.spacing.md.dp
    val lg = DesignSystem.spacing.lg.dp
    val xl = DesignSystem.spacing.xl.dp
}

/** Accès aux rayons d'angle en dp : `Radius.md`. */
object Radius {
    val sm = DesignSystem.radius.sm.dp
    val md = DesignSystem.radius.md.dp
    val lg = DesignSystem.radius.lg.dp
}

/** Accès aux tailles d'éléments en dp : `Sizes.thumbnail`. */
object Sizes {
    val thumbnail = DesignSystem.sizes.thumbnail.dp
    val heroImage = DesignSystem.sizes.heroImage.dp
    val accentSwatch = DesignSystem.sizes.accentSwatch.dp
    val selectionBorder = DesignSystem.sizes.selectionBorder.dp
    val chart = DesignSystem.sizes.chart.dp
    val calendarDay = DesignSystem.sizes.calendarDay.dp
}

/** Couleurs du design system sans rôle Material 3 équivalent : `MvpColors.highlight`. */
object MvpColors {
    /** Fond d'un élément mis en évidence (tâche ouverte par un lien), texte en `onSurface`. */
    val highlight: Color
        @Composable get() = LocalMvpTheme.current.colors.highlight.toColor()

    /** Couleur principale qu'aurait l'app avec cet accent, dans le thème actuel (pastilles du choix d'accent). */
    @Composable
    fun accentPreview(accent: AccentColor): Color = DesignSystem.colors(LocalMvpTheme.current.dark, accent).primary.toColor()
}

private fun Long.toColor() = Color(this)

private fun ColorTokens.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary.toColor(),
        onPrimary = onPrimary.toColor(),
        secondary = secondary.toColor(),
        onSecondary = onSecondary.toColor(),
        background = background.toColor(),
        onBackground = onBackground.toColor(),
        surface = surface.toColor(),
        onSurface = onSurface.toColor(),
        error = error.toColor(),
        onError = onError.toColor(),
    )
}

private fun TextStyleToken.toTextStyle() = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = FontWeight(weight.value),
)
