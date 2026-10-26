@file:OptIn(ExperimentalForeignApi::class)

package com.mvp.core.notifications

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLAccuracyAuthorizationFullAccuracy
import platform.CoreLocation.CLCircularRegion
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.UserNotifications.UNLocationNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound

/**
 * Rappels par lieu (ADR 0023) : `UNLocationNotificationTrigger`, une région circulaire à l'entrée, répétée. iOS
 * surveille la zone lui-même et affiche la notification : l'app n'est jamais réveillée et l'autorisation « Lorsque
 * l'app est active » suffit. La « Position exacte » est nécessaire. Pas d'expiration possible : voir l'ADR.
 */
internal class UserNotificationsLocationNotificationScheduler : LocationNotificationScheduler {

    // CLLocationManager se crée sur le main thread.
    override suspend fun canDeliver(): Boolean = withContext(Dispatchers.Main) {
        val manager = CLLocationManager()
        val status = manager.authorizationStatus
        val authorized = status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways
        authorized && manager.accuracyAuthorization == CLAccuracyAuthorizationFullAccuracy
    }

    override suspend fun replaceAll(group: String, notifications: List<LocationNotification>) {
        val registered = notificationCenter.pendingIdentifiers(group).toSet()
        val wanted = if (canDeliver()) notifications else emptyList()
        val changes = planLocationChanges(group, registered, wanted)
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(changes.toRemove.toList())
        changes.toAdd.forEach { (identifier, notification) -> add(identifier, notification) }
    }

    override suspend fun cancelAll(group: String) = notificationCenter.removeGroup(group)

    private suspend fun add(identifier: String, notification: LocationNotification) {
        val content = UNMutableNotificationContent().apply {
            setTitle(notification.title)
            setBody(notification.body)
            setSound(UNNotificationSound.defaultSound)
            notification.deepLink?.let { setUserInfo(mapOf<Any?, Any?>(DEEP_LINK_KEY to it)) }
        }
        val region = CLCircularRegion(
            center = CLLocationCoordinate2DMake(notification.latitude, notification.longitude),
            radius = notification.radiusMeters.toDouble(),
            identifier = identifier,
        ).apply {
            notifyOnEntry = true
            notifyOnExit = false
        }
        val trigger = UNLocationNotificationTrigger.triggerWithRegion(region, repeats = true)
        notificationCenter.addRequest(UNNotificationRequest.requestWithIdentifier(identifier, content, trigger))
    }
}
