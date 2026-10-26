package com.mvp.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager

/**
 * Reçoit les alertes de proximité posées par [ProximityLocationNotificationScheduler] et poste le rappel à l'entrée
 * dans la zone (jamais à la sortie). Non exporté : seul le système (qui détient le `PendingIntent`) peut l'appeler.
 * Une tâche ne rappelle qu'une fois par heure au plus : à la limite de la zone, le GPS peut faire entrer et sortir
 * plusieurs fois de suite.
 */
class LocationNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION || !intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)) return
        val tag = intent.getStringExtra(KEY_TAG) ?: return
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = preferences.getLong(tag, 0L)
        if (now - last in 0 until COOLDOWN_MS) return
        // Seulement l'heure des envois de la dernière heure : pas d'historique des arrivées au fil du temps.
        val editor = preferences.edit()
        preferences.all.forEach { (key, value) -> if (value !is Long || now - value !in 0 until COOLDOWN_MS) editor.remove(key) }
        editor.putLong(tag, now).commit()
        ReminderNotifications.post(
            context,
            tag = tag,
            title = intent.getStringExtra(KEY_TITLE).orEmpty(),
            body = intent.getStringExtra(KEY_BODY).orEmpty(),
            deepLink = intent.getStringExtra(KEY_DEEP_LINK),
            onlyAlertOnce = true,
        )
    }

    internal companion object {
        const val ACTION = "com.mvp.core.notifications.LOCATION_ALERT"
        const val KEY_TAG = "tag"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_DEEP_LINK = "deepLink"
        private const val PREFERENCES = "location_notifications_delivered"
        private const val COOLDOWN_MS = 60 * 60 * 1000L

        /**
         * Oublie les heures d'envoi du [group], sauf celles des étiquettes [keep] (rappels encore posés) : elles nomment
         * les tâches par leur id. Tout est oublié à la déconnexion.
         */
        fun forgetDeliveries(context: Context, group: String, keep: Set<String> = emptySet()) {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val editor = preferences.edit()
            preferences.all.keys.filter { it.startsWith("$group:") && it !in keep }.forEach(editor::remove)
            editor.commit()
        }
    }
}
