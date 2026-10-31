package com.mvp.core.designsystem

enum class FontWeightToken(val value: Int) { Regular(400), Medium(500), SemiBold(600), Bold(700) }

/** Style de texte en sp (Android) / points (iOS). */
data class TextStyleToken(
    val size: Float,
    val lineHeight: Float,
    val weight: FontWeightToken,
)

data class TypographyTokens(
    /** Grand chiffre (compte à rebours du mode focus, ADR 0028). */
    val display: TextStyleToken = TextStyleToken(64f, 72f, FontWeightToken.Regular),
    val titleLarge: TextStyleToken = TextStyleToken(28f, 34f, FontWeightToken.Bold),
    val titleMedium: TextStyleToken = TextStyleToken(20f, 26f, FontWeightToken.SemiBold),
    val body: TextStyleToken = TextStyleToken(16f, 22f, FontWeightToken.Regular),
    val label: TextStyleToken = TextStyleToken(14f, 18f, FontWeightToken.Medium),
    val caption: TextStyleToken = TextStyleToken(12f, 16f, FontWeightToken.Regular),
)
