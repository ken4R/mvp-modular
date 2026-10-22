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
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Poste la notification programmée par [WorkManagerLocalNotificationScheduler]. Instancié par WorkManager. */
class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val tag = inputData.getString(KEY_TAG) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val deepLink = inputData.getString(KEY_DEEP_LINK)
        val context = applicationContext
        val manager = context.getSystemService(NotificationManager::class.java) ?: return Result.failure()

        // Permission retirée ou notifications coupées dans les paramètres système : rien n'est affiché.
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted || !manager.areNotificationsEnabled()) return Result.success()

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            // Écran verrouillé sécurisé : contenu masqué (le texte peut être une donnée personnelle).
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openAppIntent(context, tag, deepLink))
            .build()
        manager.notify(tag, NOTIFICATION_ID, notification)
        return Result.success()
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

    internal companion object {
        const val KEY_TAG = "tag"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_DEEP_LINK = "deepLink"
        private const val CHANNEL_ID = "reminders"
        private const val CHANNEL_NAME = "Rappels"

        // Les notifications sont distinguées par leur tag (groupe + id) : l'id numérique est fixe.
        private const val NOTIFICATION_ID = 1
    }
}
