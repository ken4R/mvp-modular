package com.mvp.core.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
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

    // Appelé hors du thread principal (dispatcher io des use cases) : la lecture des travaux peut bloquer.
    override suspend fun replaceAll(group: String, notifications: List<LocalNotification>) {
        val currentTime = now()
        // Échus mais pas encore partis (téléphone éteint, veille) : on les laisse partir. Les autres
        // sont annulés ; les opérations WorkManager s'exécutent dans l'ordre, avant les nouveaux travaux.
        workManager.getWorkInfosByTag(group).get()
            .filterNot { it.state == WorkInfo.State.ENQUEUED && (it.scheduledAt() ?: Long.MAX_VALUE) <= currentTime }
            .forEach { workManager.cancelWorkById(it.id) }
        notifications.filter { it.atEpochMillis > currentTime }.forEach { notification ->
            val tag = notificationTag(group, notification.id)
            val request = OneTimeWorkRequest.Builder(NotificationWorker::class.java)
                .setInitialDelay(notification.atEpochMillis - currentTime, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(NotificationWorker.KEY_TAG, tag)
                        .putString(NotificationWorker.KEY_TITLE, notification.title)
                        .putString(NotificationWorker.KEY_BODY, notification.body)
                        .putString(NotificationWorker.KEY_DEEP_LINK, notification.deepLink)
                        .build(),
                )
                .addTag(group)
                .addTag("$AT_TAG_PREFIX${notification.atEpochMillis}")
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

/** Heure prévue d'un travail, gardée dans une étiquette « at:<epoch ms> ». */
private fun WorkInfo.scheduledAt(): Long? =
    tags.firstOrNull { it.startsWith(AT_TAG_PREFIX) }?.removePrefix(AT_TAG_PREFIX)?.toLongOrNull()

private const val AT_TAG_PREFIX = "at:"
