package com.mvp.core.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * Affichage commun des rappels, à l'heure ([NotificationWorker]) ou par lieu ([LocationNotificationReceiver]) :
 * canal « Rappels », contenu masqué sur l'écran verrouillé sécurisé, un tap ouvre l'app sur [deepLink].
 */
internal object ReminderNotifications {

    /** Rien n'est affiché si la permission est retirée ou les notifications coupées dans les paramètres système. */
    /** [onlyAlertOnce] : rappel par lieu reçu de nouveau alors qu'il est encore affiché, mis à jour sans nouveau son. */
    fun post(context: Context, tag: String, title: String, body: String, deepLink: String?, onlyAlertOnce: Boolean = false) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted || !manager.areNotificationsEnabled()) return

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setOnlyAlertOnce(onlyAlertOnce)
            // Écran verrouillé sécurisé : contenu masqué (le texte peut être une donnée personnelle).
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openAppIntent(context, tag, deepLink))
            .build()
        manager.notify(tag, NOTIFICATION_ID, notification)
    }

    /** Retire les notifications affichées dont l'étiquette commence par « [group]: ». */
    fun cancelDelivered(context: Context, group: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.activeNotifications
            .filter { it.tag?.startsWith("$group:") == true }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    /**
     * Un tap sur la notification ouvre l'activité de lancement de l'app, avec [deepLink] comme données
     * (l'activité le lit comme un lien externe) ; sans lien, sur son écran de lancement. Intent explicite :
     * aucune autre app ne peut le recevoir. Un code de requête par notification : chacune garde son lien.
     */
    private fun openAppIntent(context: Context, tag: String, deepLink: String?): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            if (deepLink != null) {
                intent.setAction(Intent.ACTION_VIEW).setData(Uri.parse(deepLink))
            }
            PendingIntent.getActivity(context, tag.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    private const val CHANNEL_ID = "reminders"
    private const val CHANNEL_NAME = "Rappels"

    // Les notifications sont distinguées par leur tag (groupe + id) : l'id numérique est fixe.
    private const val NOTIFICATION_ID = 1
}
