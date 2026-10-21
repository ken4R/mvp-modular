package com.mvp.core.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Un travail WorkManager par notification, étiqueté par groupe : il survit au redémarrage de l'app
 * et du téléphone, sans permission d'alarme exacte. En veille (Doze), l'envoi peut glisser de
 * quelques minutes (ADR 0004). Le worker [NotificationWorker] poste la notification.
 */
internal class WorkManagerLocalNotificationScheduler(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : LocalNotificationScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override suspend fun replaceAll(group: String, notifications: List<LocalNotification>) {
        // Les opérations WorkManager s'exécutent dans l'ordre : l'annulation passe avant les nouveaux travaux.
        workManager.cancelAllWorkByTag(group)
        val currentTime = now()
        notifications.filter { it.atEpochMillis > currentTime }.forEach { notification ->
            val tag = notificationTag(group, notification.id)
            val request = OneTimeWorkRequest.Builder(NotificationWorker::class.java)
                .setInitialDelay(notification.atEpochMillis - currentTime, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(NotificationWorker.KEY_TAG, tag)
                        .putString(NotificationWorker.KEY_TITLE, notification.title)
                        .putString(NotificationWorker.KEY_BODY, notification.body)
                        .build(),
                )
                .addTag(group)
                .build()
            workManager.enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun cancelAll(group: String) {
        workManager.cancelAllWorkByTag(group)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.activeNotifications
            .filter { it.tag?.startsWith("$group:") == true }
            .forEach { manager.cancel(it.tag, it.id) }
    }
}

internal fun notificationTag(group: String, id: String) = "$group:$id"
