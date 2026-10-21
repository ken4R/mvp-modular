package com.mvp.feature.todo

import com.mvp.core.notifications.LocalNotification
import com.mvp.core.notifications.LocalNotificationScheduler
import com.mvp.feature.todo.data.reminder.NotificationReminderScheduler
import com.mvp.feature.todo.domain.model.Todo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationReminderSchedulerTest {

    private class RecordingScheduler : LocalNotificationScheduler {
        val calls = mutableListOf<Pair<String, List<LocalNotification>?>>()
        override suspend fun replaceAll(group: String, notifications: List<LocalNotification>) {
            calls += group to notifications
        }
        override suspend fun cancelAll(group: String) {
            calls += group to null
        }
    }

    private val notifications = RecordingScheduler()
    private val scheduler = NotificationReminderScheduler(notifications)

    @Test
    fun eachReminderBecomesANotificationOfTheTodoGroup() = runTest {
        scheduler.replaceAll(
            listOf(
                Todo("a", "Appeler Paul", isDone = false, reminderAt = 42L),
                Todo("b", "Sans rappel", isDone = false),
            ),
        )
        scheduler.cancelAll()

        assertEquals(
            listOf(
                "todo-reminders" to listOf(LocalNotification(id = "a", title = "Rappel", body = "Appeler Paul", atEpochMillis = 42L)),
                "todo-reminders" to null,
            ),
            notifications.calls,
        )
    }
}
