package com.mvp.feature.focus

import com.mvp.core.common.domain.applock.AppLockPreference
import com.mvp.core.common.domain.notification.NotificationsPreference
import com.mvp.core.common.domain.session.SessionExpiry
import com.mvp.core.notifications.LocalNotification
import com.mvp.core.notifications.LocalNotificationScheduler
import com.mvp.core.securestorage.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Stockage « chiffré » en mémoire : recréer un dépôt dessus simule un redémarrage de l'app. */
class InMemorySecureStorage : SecureStorage {
    val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

/** Notifications programmées, par groupe. */
class FakeNotificationScheduler : LocalNotificationScheduler {
    val scheduled = mutableMapOf<String, List<LocalNotification>>()
    override suspend fun replaceAll(group: String, notifications: List<LocalNotification>) {
        scheduled[group] = notifications
    }
    override suspend fun cancelAll(group: String) {
        scheduled.remove(group)
    }
}

class FakeNotificationsPreference(enabled: Boolean = true) : NotificationsPreference {
    val enabled = MutableStateFlow(enabled)
    override fun observeNotificationsEnabled(): Flow<Boolean> = enabled
}

class FakeAppLockPreference(enabled: Boolean = false) : AppLockPreference {
    val enabled = MutableStateFlow(enabled)
    override fun observeAppLockEnabled(): Flow<Boolean> = enabled
}

class FakeSessionExpiry(expiresAt: Long?) : SessionExpiry {
    val expiresAt = MutableStateFlow(expiresAt)
    override fun observeExpiresAt(): Flow<Long?> = expiresAt
}
