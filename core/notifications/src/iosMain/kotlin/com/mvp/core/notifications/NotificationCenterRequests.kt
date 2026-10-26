package com.mvp.core.notifications

import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Accès communs à `UNUserNotificationCenter`, pour les rappels à l'heure et par lieu. Identifiants « groupe:… ». */
internal val notificationCenter: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()

internal suspend fun UNUserNotificationCenter.addRequest(request: UNNotificationRequest): Unit = suspendCoroutine { continuation ->
    addNotificationRequest(request) { error ->
        // Remonté au use case de synchronisation, qui réessaie à la prochaine émission.
        if (error == null) continuation.resume(Unit) else continuation.resumeWithException(IllegalStateException(error.localizedDescription))
    }
}

internal suspend fun UNUserNotificationCenter.pendingIdentifiers(group: String): List<String> = suspendCoroutine { continuation ->
    getPendingNotificationRequestsWithCompletionHandler { requests ->
        val identifiers = requests.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier }
        continuation.resume(identifiers.filter { it.startsWith("$group:") })
    }
}

internal suspend fun UNUserNotificationCenter.deliveredIdentifiers(group: String): List<String> = suspendCoroutine { continuation ->
    getDeliveredNotificationsWithCompletionHandler { notifications ->
        val identifiers = notifications.orEmpty().mapNotNull { (it as? UNNotification)?.request?.identifier }
        continuation.resume(identifiers.filter { it.startsWith("$group:") })
    }
}

/** Retire les notifications programmées et affichées du [group]. */
internal suspend fun UNUserNotificationCenter.removeGroup(group: String) {
    removePendingNotificationRequestsWithIdentifiers(pendingIdentifiers(group))
    removeDeliveredNotificationsWithIdentifiers(deliveredIdentifiers(group))
}
