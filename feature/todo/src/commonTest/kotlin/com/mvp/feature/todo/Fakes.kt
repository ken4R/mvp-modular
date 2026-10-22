package com.mvp.feature.todo

import com.mvp.core.common.domain.analytics.AnalyticsEvent
import com.mvp.core.common.domain.analytics.AnalyticsTracker
import com.mvp.core.common.domain.applock.AppLockPreference
import com.mvp.core.common.domain.connectivity.ConnectivityObserver
import com.mvp.core.common.domain.notification.NotificationsPreference
import com.mvp.core.common.domain.session.SessionExpiry
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

/** Session pilotée par le test : `null` = déconnecté. */
class FakeSessionExpiry(expiresAt: Long?) : SessionExpiry {
    val expiresAt = MutableStateFlow(expiresAt)
    override fun observeExpiresAt(): Flow<Long?> = expiresAt
}

/** Garde les événements de mesure reçus, consentement compris (le filtre est testé dans core:analytics). */
class FakeAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()
    override suspend fun track(event: AnalyticsEvent) {
        events += event
    }
}

/** Réglage Sécurité (déverrouillage Face ID / empreinte) piloté par le test. */
class FakeAppLockPreference(enabled: Boolean) : AppLockPreference {
    val enabled = MutableStateFlow(enabled)
    override fun observeAppLockEnabled(): Flow<Boolean> = enabled
}

/** Accès à internet piloté par le test. */
class FakeConnectivityObserver(online: Boolean) : ConnectivityObserver {
    val online = MutableStateFlow(online)
    override fun observeIsOnline(): Flow<Boolean> = online
}
