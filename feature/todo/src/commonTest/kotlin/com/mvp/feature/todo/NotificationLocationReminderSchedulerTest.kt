package com.mvp.feature.todo

import com.mvp.core.notifications.LocationNotification
import com.mvp.core.notifications.LocationNotificationScheduler
import com.mvp.feature.todo.data.reminder.NotificationLocationReminderScheduler
import com.mvp.feature.todo.domain.model.LocationReminder
import com.mvp.feature.todo.domain.model.Todo
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationLocationReminderSchedulerTest {

    private class RecordingScheduler : LocationNotificationScheduler {
        var deliverable = true
        val calls = mutableListOf<Pair<String, List<LocationNotification>?>>()
        override suspend fun canDeliver(): Boolean = deliverable
        override suspend fun replaceAll(group: String, notifications: List<LocationNotification>) {
            calls += group to notifications
        }
        override suspend fun cancelAll(group: String) {
            calls += group to null
        }
    }

    private val notifications = RecordingScheduler()
    private val scheduler = NotificationLocationReminderScheduler(notifications)

    @Test
    fun eachPlaceBecomesANotificationOfTheLocationGroupWithoutItsLabel() = runTest {
        scheduler.replaceAll(
            listOf(
                Todo("a", "Imprimer", isDone = false, locationReminder = LocationReminder(48.85, 2.35, 200, "Bureau")),
                Todo("b", "Sans lieu", isDone = false),
            ),
            expiresAt = 99L,
        )
        scheduler.cancelAll()

        assertEquals(
            listOf(
                "todo-location-reminders" to listOf(
                    LocationNotification(
                        id = "a",
                        title = "Rappel",
                        body = "Imprimer",
                        latitude = 48.85,
                        longitude = 2.35,
                        radiusMeters = 200,
                        deepLink = "mvpmodular://tasks/a",
                        expiresAtEpochMillis = 99L,
                    ),
                ),
                "todo-location-reminders" to null,
            ),
            notifications.calls,
        )
    }

    @Test
    fun availabilityIsReadAgainOnRefresh() = runTest {
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { scheduler.observeAvailability().toList(seen) }
        assertEquals(listOf(true), seen)

        // Les permissions changées dans les paramètres du système ne préviennent pas l'app : relues au rafraîchissement.
        notifications.deliverable = false
        assertEquals(listOf(true), seen)
        scheduler.refreshAvailability()
        assertEquals(listOf(true, false), seen)

        // Inchangées : pas de nouvelle émission.
        scheduler.refreshAvailability()
        assertEquals(listOf(true, false), seen)
    }
}
