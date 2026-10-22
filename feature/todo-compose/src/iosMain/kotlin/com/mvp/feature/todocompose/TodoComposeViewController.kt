package com.mvp.feature.todocompose

import androidx.compose.ui.window.ComposeUIViewController
import com.mvp.feature.todocompose.ui.TodoComposeScreen
import platform.UIKit.UIViewController

/**
 * L'écran Compose dans un UIViewController, que SwiftUI affiche via UIViewControllerRepresentable
 * (package iOS FeatureTodoCompose). Exposé à Swift par `ComposeScreens` dans shared.
 * Compose dessine lui-même chaque pixel (Skia) : aucune vue UIKit native à l'intérieur.
 */
fun todoComposeViewController(onClose: (() -> Unit)?): UIViewController =
    ComposeUIViewController { TodoComposeScreen(onClose = onClose) }
