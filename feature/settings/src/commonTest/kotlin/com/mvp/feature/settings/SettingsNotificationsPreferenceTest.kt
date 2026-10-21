package com.mvp.feature.settings

import com.mvp.feature.settings.data.notification.SettingsNotificationsPreference
import com.mvp.feature.settings.data.repository.SettingsRepositoryImpl
import com.mvp.feature.settings.data.source.InMemorySettingsLocalDataSource
import com.mvp.feature.settings.data.source.SecureProfileLocalDataSource
import com.mvp.feature.settings.domain.model.UserProfile
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsNotificationsPreferenceTest {

    private val local = InMemorySettingsLocalDataSource()
    private val repository = SettingsRepositoryImpl(local, SecureProfileLocalDataSource(InMemorySecureStorage(), legacy = local))
    private val preference = SettingsNotificationsPreference(repository)

    @Test
    fun followsNotificationsSettingOnly() = TestScope().runTest {
        val emissions = mutableListOf<Boolean>()
        val job = launch { preference.observeNotificationsEnabled().collect { emissions += it } }
        testScheduler.advanceUntilIdle()

        repository.setNotificationsEnabled(true)
        testScheduler.advanceUntilIdle()
        // Un autre réglage ne réémet pas la même valeur.
        repository.updateProfile(UserProfile("Youssef", ""))
        testScheduler.advanceUntilIdle()
        repository.setNotificationsEnabled(false)
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(false, true, false), emissions)
    }
}
