package com.mvp.feature.settings.data.repository

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.settings.data.source.SettingsLocalDataSource
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.model.UserProfile
import com.mvp.feature.settings.domain.model.UserSettings
import com.mvp.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

internal class SettingsRepositoryImpl(
    private val local: SettingsLocalDataSource,
) : SettingsRepository {

    override fun observeSettings(): Flow<UserSettings> = local.observe()

    override suspend fun updateProfile(profile: UserProfile): AppResult<UserProfile> = safeCall {
        local.update { it.copy(profile = profile) }
        profile
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean): AppResult<Unit> = safeCall {
        local.update { it.copy(notificationsEnabled = enabled) }
    }

    override suspend fun setConsent(consent: Consent): AppResult<Unit> = safeCall {
        local.update { settings ->
            settings.copy(consents = settings.consents.filterNot { it.type == consent.type } + consent)
        }
    }
}
