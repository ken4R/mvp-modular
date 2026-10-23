package com.mvp.android.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Écrans au contenu sensible (pièces jointes, ADR 0014) : tant qu'un est affiché, MainActivity masque l'aperçu
 * de l'app dans les apps récentes, même sans le verrou Face ID / empreinte.
 */
object RecentsPrivacy {
    private val sensitiveScreens = MutableStateFlow(0)

    val hasSensitiveScreen: StateFlow<Int> = sensitiveScreens.asStateFlow()

    /** À appeler dans un écran sensible : l'aperçu est masqué tant qu'il est composé. */
    @Composable
    fun HideWhileShown() {
        DisposableEffect(Unit) {
            sensitiveScreens.update { it + 1 }
            onDispose { sensitiveScreens.update { it - 1 } }
        }
    }
}
