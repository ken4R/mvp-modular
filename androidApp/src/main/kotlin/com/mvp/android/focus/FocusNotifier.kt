package com.mvp.android.focus

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.util.Log
import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.focus.domain.model.FocusFormat
import com.mvp.feature.focus.domain.model.FocusState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Notification en cours du mode focus (ADR 0028), tenue tant que le processus de l'app vit : redessinée à chaque
 * changement de la session ([states], ObserveFocusUseCase), retirée sans session (arrêt, déconnexion).
 *
 * Pas de service au premier plan : le compte à rebours est celui du système (chronomètre décompté jusqu'à `endsAt`)
 * et la notification se retire seule à la fin de la phase (`setTimeoutAfter`), où l'alerte de fin programmée par KMP
 * prend le relais, même app arrêtée. Si l'app vit encore, elle est remplacée par « terminée » avec l'action suivante.
 */
class FocusNotifier(private val context: Context, private val states: Flow<AppResult<FocusState?>>) {

    fun start(scope: CoroutineScope) {
        states
            .onEach { result ->
                // Un échec d'affichage ne doit pas faire tomber l'app (portée sans gestionnaire d'erreur).
                try {
                    when (result) {
                        is AppResult.Success -> result.data?.let(::show) ?: cancel()
                        is AppResult.Failure -> cancel()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("Focus", "Notification du mode focus impossible", e)
                }
            }
            .launchIn(scope)
    }

    private fun show(state: FocusState) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) return
        // Importance basse : pas de son ni de bandeau, seulement dans le volet et sur l'écran verrouillé.
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW))

        // Verrou de l'app activé : jamais le titre de la tâche hors de l'app.
        val title = state.lockScreenTitle ?: HIDDEN_TITLE
        val text = statusText(state)
        val builder = baseBuilder(state)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(state.link))
            // Écran verrouillé sécurisé : la version publique, sans le titre de la tâche.
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(baseBuilder(state).setContentTitle(HIDDEN_TITLE).setContentText(text).build())
        when {
            state.isRunning -> builder
                .addAction(action(FocusActionReceiver.ACTION_PAUSE, "Pause"))
                .addAction(action(FocusActionReceiver.ACTION_STOP, "Arrêter"))
            state.isPaused -> builder
                .addAction(action(FocusActionReceiver.ACTION_RESUME, "Reprendre"))
                .addAction(action(FocusActionReceiver.ACTION_STOP, "Arrêter"))
            else -> builder
                .addAction(action(FocusActionReceiver.ACTION_NEXT, if (state.nextPhase.isBreak) "Commencer la pause" else "Reprendre"))
                .addAction(action(FocusActionReceiver.ACTION_STOP, "Arrêter"))
        }
        manager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, builder.build())
    }

    private fun baseBuilder(state: FocusState): Notification.Builder {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(!state.isFinished)
            .setOnlyAlertOnce(true)
            .setShowWhen(state.isRunning)
        val endsAt = state.endsAt
        if (state.isRunning && endsAt != null) {
            builder.setWhen(endsAt)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setTimeoutAfter((endsAt - System.currentTimeMillis()).coerceAtLeast(1))
        }
        return builder
    }

    private fun statusText(state: FocusState): String = when {
        state.isRunning -> state.phaseLabel
        state.isPaused -> "${state.phaseLabel} en pause · ${FocusFormat.remaining(state.remainingMillis)} restantes"
        else -> "${state.phaseLabel} terminée · ensuite : ${state.nextPhaseLabel.lowercase()}"
    }

    private fun cancel() {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_TAG, NOTIFICATION_ID)
    }

    /** Action vers [FocusActionReceiver], non exporté : intent explicite, PendingIntent immuable. */
    private fun action(action: String, label: String): Notification.Action {
        val intent = Intent(context, FocusActionReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(null as Icon?, label, pending).build()
    }

    /** Un tap ouvre l'app sur la tâche, par le même chemin qu'un lien profond (vérifié par la connexion et le verrou). */
    private fun openAppIntent(link: String): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            intent.setAction(Intent.ACTION_VIEW).setData(Uri.parse(link))
            PendingIntent.getActivity(context, NOTIFICATION_TAG.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    private companion object {
        const val CHANNEL_ID = "focus"
        const val CHANNEL_NAME = "Mode focus"
        const val NOTIFICATION_TAG = "focus:ongoing"
        const val NOTIFICATION_ID = 1
        const val HIDDEN_TITLE = "Session de concentration"
    }
}
