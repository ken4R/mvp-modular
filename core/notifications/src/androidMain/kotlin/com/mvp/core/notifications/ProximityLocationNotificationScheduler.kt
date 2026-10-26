package com.mvp.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Alertes de proximité du `LocationManager` (sans Google Play Services, ADR 0023). Chaque alerte porte un
 * `PendingIntent` explicite vers [LocationNotificationReceiver], qui poste la notification à l'entrée dans la zone.
 *
 * Les identifiants posés (« groupe:id:empreinte », jamais de coordonnées) sont gardés dans des préférences privées,
 * exclues des sauvegardes et des transferts d'appareil (`data_extraction_rules.xml` de l'app),
 * pour retirer plus tard les alertes qui ne sont plus voulues. Une alerte n'est tenue pour posée que si son
 * `PendingIntent` existe encore : un redémarrage du téléphone ou un arrêt forcé de l'app les efface tous.
 */
internal class ProximityLocationNotificationScheduler(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : LocationNotificationScheduler {

    // Lancé à la fois au démarrage du processus et par le récepteur du redémarrage : jamais deux à la fois.
    private val mutex = Mutex()
    private val preferences get() = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override suspend fun canDeliver(): Boolean {
        val fine = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        // Android 10+ : sans « Toujours autoriser », le système ne surveille pas la zone app fermée.
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        return fine && background
    }

    override suspend fun replaceAll(group: String, notifications: List<LocationNotification>) = mutex.withLock {
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withLock
        val registered = registered(group).filterTo(mutableSetOf()) { existingPendingIntent(it) != null }
        val wanted = if (canDeliver()) notifications.filter { it.isActive() } else emptyList()
        val changes = planLocationChanges(group, registered, wanted)
        changes.toRemove.forEach { remove(manager, it) }
        val added = changes.toAdd.mapNotNull { (identifier, notification) ->
            identifier.takeIf { add(manager, group, it, notification) }
        }
        save(group, registered - changes.toRemove + added)
        LocationNotificationReceiver.forgetDeliveries(context, group, keep = wanted.map { it.displayTag(group) }.toSet())
    }

    override suspend fun cancelAll(group: String) = mutex.withLock {
        context.getSystemService(LocationManager::class.java)?.let { manager ->
            registered(group).forEach { remove(manager, it) }
        }
        preferences.edit().remove(registeredKey(group)).commit()
        LocationNotificationReceiver.forgetDeliveries(context, group)
        ReminderNotifications.cancelDelivered(context, group)
    }

    private fun LocationNotification.isActive() = expiresAtEpochMillis?.let { it > now() } ?: true

    /** `false` si la permission a été retirée entre-temps : l'alerte sera reposée à la réconciliation suivante. */
    @SuppressLint("MissingPermission")
    private fun add(manager: LocationManager, group: String, identifier: String, notification: LocationNotification): Boolean {
        val intent = alertIntent(identifier)
            .putExtra(LocationNotificationReceiver.KEY_TAG, notification.displayTag(group))
            .putExtra(LocationNotificationReceiver.KEY_TITLE, notification.title)
            .putExtra(LocationNotificationReceiver.KEY_BODY, notification.body)
            .putExtra(LocationNotificationReceiver.KEY_DEEP_LINK, notification.deepLink)
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag())
        // -1 : sans expiration. Sinon jusqu'à la fin de la session, même app fermée.
        val expiration = notification.expiresAtEpochMillis?.let { (it - now()).coerceAtLeast(1L) } ?: -1L
        return try {
            manager.addProximityAlert(
                notification.latitude,
                notification.longitude,
                notification.radiusMeters.toFloat(),
                expiration,
                pendingIntent,
            )
            true
        } catch (e: SecurityException) {
            pendingIntent.cancel()
            false
        }
    }

    private fun remove(manager: LocationManager, identifier: String) {
        val pendingIntent = existingPendingIntent(identifier) ?: return
        manager.removeProximityAlert(pendingIntent)
        pendingIntent.cancel()
    }

    private fun existingPendingIntent(identifier: String): PendingIntent? =
        PendingIntent.getBroadcast(context, 0, alertIntent(identifier), PendingIntent.FLAG_NO_CREATE or mutableFlag())

    /**
     * Intent explicite (seul [LocationNotificationReceiver], non exporté, peut le recevoir), une donnée par alerte :
     * deux `PendingIntent` ne sont distincts que si leurs intents diffèrent hors extras.
     */
    private fun alertIntent(identifier: String): Intent =
        Intent(context, LocationNotificationReceiver::class.java)
            .setAction(LocationNotificationReceiver.ACTION)
            .setData(Uri.fromParts(URI_SCHEME, identifier, null))

    /** Le système ajoute `KEY_PROXIMITY_ENTERING` à l'intent : il doit être modifiable (Android 12+). */
    private fun mutableFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

    private fun isGranted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun registered(group: String): Set<String> =
        preferences.getStringSet(registeredKey(group), null)?.toSet().orEmpty()

    // commit() : écrit avant de rendre la main (hors thread principal), pour qu'une alerte posée soit toujours dans la
    // liste, même si le processus est tué juste après, et donc retirée à la déconnexion.
    private fun save(group: String, identifiers: Set<String>) {
        preferences.edit().putStringSet(registeredKey(group), identifiers).commit()
    }

    private fun registeredKey(group: String) = "registered:$group"

    private companion object {
        const val PREFERENCES = "location_notifications"
        const val URI_SCHEME = "mvp-location-alert"
    }
}
