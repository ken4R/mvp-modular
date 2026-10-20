package com.mvp.feature.settings

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.settings.data.repository.SettingsRepositoryImpl
import com.mvp.feature.settings.data.source.InMemorySettingsLocalDataSource
import com.mvp.feature.settings.data.source.SecureProfileLocalDataSource
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.model.ConsentType
import com.mvp.feature.settings.domain.model.UserProfile
import com.mvp.feature.settings.domain.model.UserSettings
import com.mvp.feature.settings.domain.usecase.ObserveSettingsUseCase
import com.mvp.feature.settings.domain.usecase.SetConsentUseCase
import com.mvp.feature.settings.domain.usecase.SetNotificationsEnabledUseCase
import com.mvp.feature.settings.domain.usecase.UpdateProfileUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsUseCasesTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val local = InMemorySettingsLocalDataSource()
    private val repository = SettingsRepositoryImpl(local, SecureProfileLocalDataSource(InMemorySecureStorage(), legacy = local))

    private val observe = ObserveSettingsUseCase(repository, dispatcher)
    private val updateProfile = UpdateProfileUseCase(repository, dispatcher)
    private val setNotifications = SetNotificationsEnabledUseCase(repository, dispatcher)
    private val setConsent = SetConsentUseCase(repository, dispatcher)

    private suspend fun current(): UserSettings = observe(NoParams).first().getOrNull()!!

    @Test
    fun firstLaunchHasNotificationsOffAndNoConsent() = scope.runTest {
        val settings = current()

        assertFalse(settings.notificationsEnabled)
        assertEquals(ConsentType.entries.toList(), settings.consents.map { it.type })
        assertTrue(settings.consents.none { it.granted })
    }

    @Test
    fun profileIsTrimmedAndSaved() = scope.runTest {
        val result = updateProfile(UserProfile("  Youssef  ", " youssef@example.com "))

        val expected = UserProfile("Youssef", "youssef@example.com")
        assertEquals(AppResult.Success(expected), result)
        assertEquals(expected, current().profile)
    }

    @Test
    fun emailIsOptional() = scope.runTest {
        assertEquals(AppResult.Success(UserProfile("Youssef", "")), updateProfile(UserProfile("Youssef", "  ")))
    }

    @Test
    fun blankNameIsRejected() = scope.runTest {
        val result = updateProfile(UserProfile("   ", ""))

        assertIs<AppError.Validation>((result as AppResult.Failure).error)
        assertEquals("", current().profile.displayName)
    }

    @Test
    fun tooLongNameIsRejected() = scope.runTest {
        val result = updateProfile(UserProfile("x".repeat(UpdateProfileUseCase.MAX_NAME_LENGTH + 1), ""))

        assertIs<AppError.Validation>((result as AppResult.Failure).error)
    }

    @Test
    fun invalidEmailIsRejected() = scope.runTest {
        val result = updateProfile(UserProfile("Youssef", "pas-un-email"))

        assertIs<AppError.Validation>((result as AppResult.Failure).error)
    }

    @Test
    fun tooLongEmailIsRejected() = scope.runTest {
        val email = "a".repeat(UpdateProfileUseCase.MAX_EMAIL_LENGTH) + "@example.com"

        val result = updateProfile(UserProfile("Youssef", email))

        assertIs<AppError.Validation>((result as AppResult.Failure).error)
    }

    @Test
    fun notificationsCanBeTurnedOnAndOff() = scope.runTest {
        setNotifications(true)
        assertTrue(current().notificationsEnabled)

        setNotifications(false)
        assertFalse(current().notificationsEnabled)
    }

    @Test
    fun consentIsUpdatedAndOrderIsKept() = scope.runTest {
        setConsent(Consent(ConsentType.Analytics, granted = true))
        setConsent(Consent(ConsentType.Marketing, granted = true))
        setConsent(Consent(ConsentType.Analytics, granted = false))

        assertEquals(
            listOf(
                Consent(ConsentType.Analytics, granted = false),
                Consent(ConsentType.Marketing, granted = true),
                Consent(ConsentType.Personalization, granted = false),
            ),
            current().consents,
        )
    }
}
