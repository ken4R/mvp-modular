package com.mvp.umbrella

import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.settings.domain.usecase.ObserveAppearanceUseCase
import com.mvp.feature.todocompose.todoComposeViewController
import kotlinx.coroutines.flow.mapNotNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import platform.UIKit.UIViewController

/**
 * Écrans écrits en Compose Multiplatform, pour Swift : chacun est un UIViewController à afficher via
 * UIViewControllerRepresentable (ADR 0010). Copie de celui de shared (ADR 0027) : il lit le thème de feature:settings,
 * dont feature:todo-compose ne dépend pas.
 */
object ComposeScreens : KoinComponent {
    /**
     * Écran Tâches partagé ; [onClose] affiche un bouton « Fermer » (`nil` : pas de bouton).
     * Il suit le thème et l'accent choisis dans Réglages (ADR 0015).
     */
    fun todo(onClose: (() -> Unit)?): UIViewController {
        val appearance = get<ObserveAppearanceUseCase>()(NoParams).mapNotNull { it.getOrNull() }
        return todoComposeViewController(onClose, appearance)
    }
}
