package com.mvp.shared

import com.mvp.feature.todocompose.todoComposeViewController
import platform.UIKit.UIViewController

/**
 * Écrans écrits en Compose Multiplatform, pour Swift : chacun est un UIViewController
 * à afficher via UIViewControllerRepresentable (ADR 0010).
 */
object ComposeScreens {
    /** Écran Tâches partagé ; [onClose] affiche un bouton « Fermer » (`nil` : pas de bouton). */
    fun todo(onClose: (() -> Unit)?): UIViewController = todoComposeViewController(onClose)
}
