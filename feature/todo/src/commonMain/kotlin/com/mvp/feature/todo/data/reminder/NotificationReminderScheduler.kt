package com.mvp.feature.todo.data.reminder

import com.mvp.core.notifications.LocalNotification
import com.mvp.core.notifications.LocalNotificationScheduler
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.reminder.ReminderScheduler

/** Un rappel = une notification locale « Rappel » portant le titre de la tâche. */
internal class NotificationReminderScheduler(private val notifications: LocalNotificationScheduler) : ReminderScheduler {

    override suspend fun replaceAll(todos: List<Todo>) = notifications.replaceAll(
        GROUP,
        todos.mapNotNull { todo ->
            todo.reminderAt?.let { at -> LocalNotification(id = todo.id, title = TITLE, body = todo.title, atEpochMillis = at) }
        },
    )

    override suspend fun cancelAll() = notifications.cancelAll(GROUP)

    private companion object {
        const val GROUP = "todo-reminders"
        const val TITLE = "Rappel"
    }
}
