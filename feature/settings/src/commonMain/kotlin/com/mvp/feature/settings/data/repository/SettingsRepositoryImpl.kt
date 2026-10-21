package com.mvp.feature.settings.data.repository

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.settings.data.source.ProfileLocalDataSource
import com.mvp.feature.settings.data.source.SettingsLocalDataSource
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.model.UserProfile
import com.mvp.feature.settings.domain.model.UserSettings
import com.mvp.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Assemble les réglages (base SQLite) et le profil (stockage chiffré). */
internal class SettingsRepositoryImpl(
    private val local: SettingsLocalDataSource,
    private val profile: ProfileLocalDataSource,
) : SettingsRepository {

    override fun observeSettings(): Flow<UserSettings> =
        combine(local.observe(), profile.observe()) { settings, profile -> settings.copy(profile = profile) }

    override suspend fun updateProfile(profile: UserProfile): AppResult<UserProfile> = safeCall {
        this.profile.save(profile)
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

    override suspend fun setAppLockEnabled(enabled: Boolean): AppResult<Unit> = safeCall {
        local.update { it.copy(appLockEnabled = enabled) }
    }
}
