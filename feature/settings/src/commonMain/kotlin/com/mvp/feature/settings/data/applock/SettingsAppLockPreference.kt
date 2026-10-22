package com.mvp.feature.settings.data.applock

import com.mvp.core.common.domain.applock.AppLockPreference
import com.mvp.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Le réglage Sécurité, lu par les features qui montrent des données hors de l'app (widget Tâches). */
internal class SettingsAppLockPreference(private val repository: SettingsRepository) : AppLockPreference {
    override fun observeAppLockEnabled(): Flow<Boolean> =
        repository.observeSettings().map { it.appLockEnabled }.distinctUntilChanged()
}
