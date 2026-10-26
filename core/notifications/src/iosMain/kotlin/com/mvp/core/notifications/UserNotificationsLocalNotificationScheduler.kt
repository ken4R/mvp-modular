package com.mvp.core.notifications

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger

/**
 * Notifications programmées dans `UNUserNotificationCenter`, identifiées par "groupe:id".
 * La permission est demandée par l'écran Réglages ; sans elle, iOS ignore simplement les envois.
 */
internal class UserNotificationsLocalNotificationScheduler(
    private val now: () -> Long = { (NSDate().timeIntervalSince1970 * 1000).toLong() },
) : LocalNotificationScheduler {

    override suspend fun replaceAll(group: String, notifications: List<LocalNotification>) {
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(notificationCenter.pendingIdentifiers(group))
        val currentTime = now()
        notifications
            .filter { it.atEpochMillis > currentTime }
            // iOS garde au plus 64 notifications programmées par app : les plus proches d'abord.
            .sortedBy { it.atEpochMillis }
            .take(MAX_PENDING)
            .forEach { add(group, it, currentTime) }
    }

    override suspend fun cancelAll(group: String) = notificationCenter.removeGroup(group)

    private suspend fun add(group: String, notification: LocalNotification, currentTime: Long) {
        val content = UNMutableNotificationContent().apply {
            setTitle(notification.title)
            setBody(notification.body)
            setSound(UNNotificationSound.defaultSound)
            // Lu par le délégué des notifications de l'app au tap (clé DEEP_LINK_KEY).
            notification.deepLink?.let { setUserInfo(mapOf<Any?, Any?>(DEEP_LINK_KEY to it)) }
        }
        val delaySeconds = (notification.atEpochMillis - currentTime) / 1000.0
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(delaySeconds, false)
        notificationCenter.addRequest(UNNotificationRequest.requestWithIdentifier("$group:${notification.id}", content, trigger))
    }

    private companion object {
        /**
         * iOS garde au plus 64 notifications programmées par app, tous groupes confondus : 20 places restent aux
         * rappels par lieu (ADR 0023).
         */
        const val MAX_PENDING = 64 - LocationNotificationScheduler.MAX_NOTIFICATIONS
    }
}

/** Clé du lien profond dans `userInfo` d'une notification ; l'app iOS lit la même clé, "deepLink". */
internal const val DEEP_LINK_KEY = "deepLink"
