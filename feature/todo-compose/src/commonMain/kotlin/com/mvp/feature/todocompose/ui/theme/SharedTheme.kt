package com.mvp.feature.todocompose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mvp.core.designsystem.ColorTokens
import com.mvp.core.designsystem.DesignSystem
import com.mvp.core.designsystem.TextStyleToken

/**
 * Thème des écrans Compose Multiplatform, construit uniquement à partir de core:designsystem.
 * Même correspondance que MvpTheme de l'app Android, mais compilé aussi pour iOS.
 */
@Composable
internal fun SharedMvpTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DesignSystem.colors(darkTheme).toColorScheme(darkTheme),
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

/** Espacements en dp : `SharedSpacing.md`. */
internal object SharedSpacing {
    val xs = DesignSystem.spacing.xs.dp
    val sm = DesignSystem.spacing.sm.dp
    val md = DesignSystem.spacing.md.dp
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
