package com.mvp.android.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Redessine un widget à chaque changement de son contenu ([states], le use case partagé du widget : tâche ajoutée,
 * cochée, déconnexion, verrou activé, nouvelle météo...) tant que le processus de l'app vit. Sans widget posé,
 * updateAll ne fait rien.
 */
class WidgetUpdater(
    private val context: Context,
    private val states: Flow<*>,
    private val widget: () -> GlanceAppWidget,
) {

    fun start(scope: CoroutineScope) {
        states
            .onEach {
                // Un échec de redessin ne doit pas faire tomber l'app (portée sans gestionnaire d'erreur).
                try {
                    widget().updateAll(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("Widget", "Mise à jour du widget impossible", e)
                }
            }
            .launchIn(scope)
    }
}
