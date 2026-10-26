package com.mvp.core.designsystem

/** Espacements et rayons, en dp (Android) / points (iOS). */
data class SpacingTokens(
    val xs: Float = 4f,
    val sm: Float = 8f,
    val md: Float = 16f,
    val lg: Float = 24f,
    val xl: Float = 32f,
)

data class RadiusTokens(
    val sm: Float = 4f,
    val md: Float = 8f,
    val lg: Float = 16f,
)

/** Tailles d'éléments récurrents (images), en dp (Android) / points (iOS). */
data class SizeTokens(
    /** Vignette carrée d'une ligne de liste. */
    val thumbnail: Float = 64f,
    /** Hauteur de la grande image en tête d'un écran de détail. */
    val heroImage: Float = 280f,
    /** Pastille du choix de couleur d'accent (Réglages), bordure comprise. */
    val accentSwatch: Float = 44f,
    /** Épaisseur de la bordure d'un élément choisi (pastille d'accent). */
    val selectionBorder: Float = 3f,
    /** Hauteur de la zone de tracé d'un graphique (statistiques des tâches). */
    val chart: Float = 220f,
    /** Hauteur d'une case du calendrier des tâches (cible tactile d'au moins 44 pt / 48 dp). */
    val calendarDay: Float = 48f,
)
