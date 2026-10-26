package com.mvp.feature.todo.data.reminder

import com.mvp.core.deeplink.domain.DeepLinks
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.core.notifications.LocationNotification
import com.mvp.core.notifications.LocationNotificationScheduler
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.reminder.LocationReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Un rappel par lieu = une notification « Rappel » portant le titre de la tâche, déclenchée à l'arrivée dans la zone
 * (ADR 0023) ; un tap dessus ouvre la tâche. Le nom du lieu n'est pas dans la notification.
 */
internal class NotificationLocationReminderScheduler(
    private val notifications: LocationNotificationScheduler,
) : LocationReminderScheduler {

    private val refreshes = MutableStateFlow(0)

    override fun observeAvailability(): Flow<Boolean> =
        refreshes.map { notifications.canDeliver() }.distinctUntilChanged()

    override fun refreshAvailability() = refreshes.update { it + 1 }

    override suspend fun replaceAll(todos: List<Todo>, expiresAt: Long) = notifications.replaceAll(
        GROUP,
        todos.mapNotNull { todo ->
            todo.locationReminder?.let { place ->
                LocationNotification(
                    id = todo.id,
                    title = TITLE,
                    body = todo.title,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    radiusMeters = place.radiusMeters,
                    deepLink = DeepLinks.url(DeepLink.Tasks(todo.id)),
                    expiresAtEpochMillis = expiresAt,
                )
            }
        },
    )

    override suspend fun cancelAll() = notifications.cancelAll(GROUP)

    private companion object {
        const val GROUP = "todo-location-reminders"
        const val TITLE = "Rappel"
    }
}
