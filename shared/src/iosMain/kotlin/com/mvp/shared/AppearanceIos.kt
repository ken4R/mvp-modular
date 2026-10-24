package com.mvp.shared

import com.mvp.core.designsystem.AccentColor
import com.mvp.core.designsystem.ThemeMode

/**
 * Accent construit depuis Swift, qui renomme les entrées des enum Kotlin : [name] est le nom Kotlin de l'entrée
 * de `AccentColor` (« Blue », « Teal », « Violet », « Orange », « Pink ») ; un nom inconnu vaut « Blue ».
 */
fun accentColor(name: String): AccentColor = AccentColor.entries.firstOrNull { it.name == name } ?: AccentColor.Blue

/** Thème construit depuis Swift : [name] est « System », « Light » ou « Dark » ; un nom inconnu vaut « System ». */
fun themeMode(name: String): ThemeMode = ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.System
