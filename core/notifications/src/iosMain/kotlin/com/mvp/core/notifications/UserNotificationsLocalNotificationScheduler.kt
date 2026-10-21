package com.mvp.core.notifications

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Notifications programmées dans `UNUserNotificationCenter`, identifiées par "groupe:id".
 * La permission est demandée par l'écran Réglages ; sans elle, iOS ignore simplement les envois.
 */
internal class UserNotificationsLocalNotificationScheduler(
    private val now: () -> Long = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
) : LocalNotificationScheduler {

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun replaceAll(group: String, notifications: List<LocalNotification>) {
        center.removePendingNotificationRequestsWithIdentifiers(pendingIdentifiers(group))
        val currentTime = now()
        notifications
            .filter { it.atEpochMillis > currentTime }
            // iOS garde au plus 64 notifications programmées par app : les plus proches d'abord.
            .sortedBy { it.atEpochMillis }
            .take(MAX_PENDING)
            .forEach { add(group, it, currentTime) }
    }

    override suspend fun cancelAll(group: String) {
        center.removePendingNotificationRequestsWithIdentifiers(pendingIdentifiers(group))
        center.removeDeliveredNotificationsWithIdentifiers(deliveredIdentifiers(group))
    }

    private suspend fun add(group: String, notification: LocalNotification, currentTime: Long) {
        val content = UNMutableNotificationContent().apply {
            setTitle(notification.title)
            setBody(notification.body)
            setSound(UNNotificationSound.defaultSound)
        }
        val delaySeconds = (notification.atEpochMillis - currentTime) / 1000.0
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(delaySeconds, false)
        val request = UNNotificationRequest.requestWithIdentifier("$group:${notification.id}", content, trigger)
        suspendCoroutine { continuation ->
            center.addNotificationRequest(request) { error ->
                // Remonté au use case de synchronisation, qui réessaie à la prochaine émission.
                if (error == null) continuation.resume(Unit) else continuation.resumeWithException(IllegalStateException(error.localizedDescription))
            }
        }
    }

    private suspend fun pendingIdentifiers(group: String): List<String> = suspendCoroutine { continuation ->
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val identifiers = requests.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier }
            continuation.resume(identifiers.filter { it.startsWith("$group:") })
        }
    }

    private suspend fun deliveredIdentifiers(group: String): List<String> = suspendCoroutine { continuation ->
        center.getDeliveredNotificationsWithCompletionHandler { notifications ->
            val identifiers = notifications.orEmpty().mapNotNull { (it as? UNNotification)?.request.identifier }
            continuation.resume(identifiers.filter { it.startsWith("$group:") })
        }
    }

    private companion object {
        const val MAX_PENDING = 64
    }
}
