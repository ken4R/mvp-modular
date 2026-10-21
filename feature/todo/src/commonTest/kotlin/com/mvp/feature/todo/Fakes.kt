package com.mvp.feature.todo

import com.mvp.core.common.domain.notification.NotificationsPreference
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.reminder.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Garde les rappels « programmés » en mémoire, à la place des notifications du système. */
class FakeReminderScheduler : ReminderScheduler {
    var scheduled: List<Todo> = emptyList()
        private set
    var cancelAllCalls = 0
        private set
    var failure: Throwable? = null

    override suspend fun replaceAll(todos: List<Todo>) {
        failure?.let { throw it }
        scheduled = todos
    }

    override suspend fun cancelAll() {
        cancelAllCalls++
        scheduled = emptyList()
    }
}

/** Réglage Notifications piloté par le test. */
class FakeNotificationsPreference(enabled: Boolean) : NotificationsPreference {
    val enabled = MutableStateFlow(enabled)
    override fun observeNotificationsEnabled(): Flow<Boolean> = enabled
}
