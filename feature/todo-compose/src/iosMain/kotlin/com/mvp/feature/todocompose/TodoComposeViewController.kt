package com.mvp.feature.todocompose

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import com.mvp.core.designsystem.Appearance
import com.mvp.feature.todocompose.ui.TodoComposeScreen
import kotlinx.coroutines.flow.Flow
import platform.UIKit.UIViewController

/**
 * L'écran Compose dans un UIViewController, que SwiftUI affiche via UIViewControllerRepresentable
 * (package iOS FeatureTodoCompose). Exposé à Swift par `ComposeScreens` dans shared.
 * Compose dessine lui-même chaque pixel (Skia) : aucune vue UIKit native à l'intérieur.
 * [appearance] : thème et accent choisis dans Réglages, suivis pendant que l'écran est affiché.
 */
fun todoComposeViewController(onClose: (() -> Unit)?, appearance: Flow<Appearance>): UIViewController =
    ComposeUIViewController {
        val current by appearance.collectAsState(Appearance.Default)
        TodoComposeScreen(onClose = onClose, appearance = current)
    }
