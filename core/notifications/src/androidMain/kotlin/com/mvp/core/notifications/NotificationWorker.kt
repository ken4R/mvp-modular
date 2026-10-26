package com.mvp.core.notifications

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Poste la notification programmée par [WorkManagerLocalNotificationScheduler]. Instancié par WorkManager. */
class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val tag = inputData.getString(KEY_TAG) ?: return Result.failure()
        ReminderNotifications.post(
            applicationContext,
            tag = tag,
            title = inputData.getString(KEY_TITLE).orEmpty(),
            body = inputData.getString(KEY_BODY).orEmpty(),
            deepLink = inputData.getString(KEY_DEEP_LINK),
        )
        return Result.success()
    }

    internal companion object {
        const val KEY_TAG = "tag"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_DEEP_LINK = "deepLink"
    }
}
