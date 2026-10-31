package com.mvp.feature.focus.data.alarm

import com.mvp.core.common.domain.notification.NotificationsPreference
import com.mvp.core.deeplink.domain.DeepLinks
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.core.notifications.LocalNotification
import com.mvp.core.notifications.LocalNotificationScheduler
import com.mvp.feature.focus.domain.model.FocusDurations
import com.mvp.feature.focus.domain.model.FocusSession
import com.mvp.feature.focus.domain.repository.FocusAlarm
import kotlinx.coroutines.flow.first

/**
 * Alerte de fin de phase par core:notifications (WorkManager / UNUserNotificationCenter), groupe [GROUP]. Elle suit
 * le réglage Notifications comme les rappels. Le texte ne contient pas le titre de la tâche : l'alerte s'affiche sur
 * l'écran verrouillé, verrou de l'app activé ou non.
 */
internal class NotificationFocusAlarm(
    private val scheduler: LocalNotificationScheduler,
    private val preference: NotificationsPreference,
    private val durations: FocusDurations,
) : FocusAlarm {

    override suspend fun schedule(session: FocusSession) {
        val endsAt = session.endsAt
        if (endsAt == null || !preference.observeNotificationsEnabled().first()) return cancel()
        val (title, body) = if (session.phase.isBreak) {
            "Pause terminée" to "Prêt pour une nouvelle concentration ?"
        } else {
            val next = durations.next(session.phase, session.completedWorkPhases + 1)
            "Concentration terminée" to "Place à la ${next.label.lowercase()} (${durations.of(next) / FocusDurations.MINUTE} min)."
        }
        scheduler.replaceAll(
            GROUP,
            listOf(
                LocalNotification(
                    id = "phase-end",
                    title = title,
                    body = body,
                    atEpochMillis = endsAt,
                    deepLink = DeepLinks.url(DeepLink.Tasks(session.todoId)),
                ),
            ),
        )
    }

    override suspend fun cancel() {
        scheduler.cancelAll(GROUP)
    }

    companion object {
        const val GROUP = "focus"
    }
}
