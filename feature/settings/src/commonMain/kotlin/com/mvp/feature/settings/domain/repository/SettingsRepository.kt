package com.mvp.feature.settings.domain.repository

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.designsystem.AccentColor
import com.mvp.core.designsystem.ThemeMode
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.model.UserProfile
import com.mvp.feature.settings.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<UserSettings>
    suspend fun updateProfile(profile: UserProfile): AppResult<UserProfile>
    suspend fun setNotificationsEnabled(enabled: Boolean): AppResult<Unit>
    suspend fun setConsent(consent: Consent): AppResult<Unit>
    suspend fun setAppLockEnabled(enabled: Boolean): AppResult<Unit>
    suspend fun setThemeMode(mode: ThemeMode): AppResult<Unit>
    suspend fun setAccentColor(accent: AccentColor): AppResult<Unit>
}
