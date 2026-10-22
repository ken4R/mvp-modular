package com.mvp.android.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.usecase.ObserveTodoWidgetUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Redessine le widget Tâches à chaque changement de son contenu (tâche ajoutée, cochée, déconnexion,
 * verrou activé...) tant que le processus de l'app vit. Sans widget posé, updateAll ne fait rien.
 */
class TodoWidgetUpdater(private val context: Context, private val observeWidget: ObserveTodoWidgetUseCase) {

    fun start(scope: CoroutineScope) {
        observeWidget(NoParams)
            .onEach { TodoWidget().updateAll(context) }
            .launchIn(scope)
    }
}
