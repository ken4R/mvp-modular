package com.mvp.feature.settings.data.notification

import com.mvp.core.common.domain.notification.NotificationsPreference
import com.mvp.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Le réglage Notifications, lu par les features qui envoient des notifications (rappels de tâches).
 * Il suit déjà la permission système : refusée ou retirée, il est coupé (voir les écrans Réglages).
 */
internal class SettingsNotificationsPreference(private val repository: SettingsRepository) : NotificationsPreference {
    override fun observeNotificationsEnabled(): Flow<Boolean> =
        repository.observeSettings().map { it.notificationsEnabled }.distinctUntilChanged()
}
