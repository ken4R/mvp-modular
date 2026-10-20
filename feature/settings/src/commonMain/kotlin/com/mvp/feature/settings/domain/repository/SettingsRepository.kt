package com.mvp.feature.settings.domain.repository

import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.model.UserProfile
import com.mvp.feature.settings.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<UserSettings>
    suspend fun updateProfile(profile: UserProfile): AppResult<UserProfile>
    suspend fun setNotificationsEnabled(enabled: Boolean): AppResult<Unit>
    suspend fun setConsent(consent: Consent): AppResult<Unit>
}
