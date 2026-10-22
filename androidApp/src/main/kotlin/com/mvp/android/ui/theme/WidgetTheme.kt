package com.mvp.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.color.ColorProvider
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mvp.core.designsystem.DesignSystem
import com.mvp.core.designsystem.TextStyleToken

/**
 * Équivalent de [MvpTheme] pour le widget Glance, qui n'utilise pas MaterialTheme : couleurs jour / nuit
 * et styles de texte tirés des tokens de core:designsystem.
 */
object WidgetTheme {
    private val light = DesignSystem.colors(darkTheme = false)
    private val dark = DesignSystem.colors(darkTheme = true)

    val surface: ColorProvider = ColorProvider(day = Color(light.surface), night = Color(dark.surface))
    val onSurface: ColorProvider = ColorProvider(day = Color(light.onSurface), night = Color(dark.onSurface))
    val primary: ColorProvider = ColorProvider(day = Color(light.primary), night = Color(dark.primary))

    val title: TextStyle = DesignSystem.typography.titleMedium.toGlance(primary)
    val body: TextStyle = DesignSystem.typography.body.toGlance(onSurface)
    val caption: TextStyle = DesignSystem.typography.caption.toGlance(onSurface)
}

// Glance ne connaît que trois graisses.
private fun TextStyleToken.toGlance(color: ColorProvider) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = when {
        weight.value >= 600 -> FontWeight.Bold
        weight.value >= 500 -> FontWeight.Medium
        else -> FontWeight.Normal
    },
)
