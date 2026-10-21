package com.mvp.core.notifications

/** Notification à afficher à [atEpochMillis]. [id] est unique dans son groupe. */
data class LocalNotification(
    val id: String,
    val title: String,
    val body: String,
    val atEpochMillis: Long,
)

/**
 * Programme des notifications locales, regroupées par feature ([group], par exemple "todo-reminders") :
 * chaque feature ne touche qu'à ses propres notifications.
 */
interface LocalNotificationScheduler {

    /** Remplace les notifications programmées du [group] par [notifications] (celles déjà passées sont ignorées). */
    suspend fun replaceAll(group: String, notifications: List<LocalNotification>)

    /** Annule les notifications programmées du [group] et retire celles déjà affichées. */
    suspend fun cancelAll(group: String)
}
