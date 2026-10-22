package com.mvp.feature.settings

import com.mvp.core.common.domain.analytics.AnalyticsConsentObserver
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.settings.data.analytics.SettingsAnalyticsConsent
import com.mvp.feature.settings.data.repository.SettingsRepositoryImpl
import com.mvp.feature.settings.data.source.InMemorySettingsLocalDataSource
import com.mvp.feature.settings.data.source.SecureProfileLocalDataSource
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.model.ConsentChoice
import com.mvp.feature.settings.domain.model.ConsentTexts
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
    private val revocations = RecordingAnalyticsConsentObserver()
    private var clock = 1_000L
    private val setConsent = SetConsentUseCase(repository, listOf(revocations), now = { clock }, dispatcher)

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
    fun consentIsUpdatedDatedAndOrderIsKept() = scope.runTest {
        setConsent(ConsentChoice(ConsentType.Analytics, granted = true))
        clock = 2_000L
        setConsent(ConsentChoice(ConsentType.Marketing, granted = true))
        clock = 3_000L
        setConsent(ConsentChoice(ConsentType.Analytics, granted = false))

        assertEquals(
            listOf(
                Consent(ConsentType.Analytics, granted = false, decidedAt = 3_000L, textVersion = ConsentTexts.of(ConsentType.Analytics).version),
                Consent(ConsentType.Marketing, granted = true, decidedAt = 2_000L, textVersion = ConsentTexts.of(ConsentType.Marketing).version),
                Consent(ConsentType.Personalization, granted = false),
            ),
            current().consents,
        )
    }

    @Test
    fun onlyRevokingAnalyticsStopsTheMeasurement() = scope.runTest {
        setConsent(ConsentChoice(ConsentType.Analytics, granted = true))
        setConsent(ConsentChoice(ConsentType.Marketing, granted = false))
        assertEquals(0, revocations.calls)

        setConsent(ConsentChoice(ConsentType.Analytics, granted = false))
        assertEquals(1, revocations.calls)
    }

    @Test
    fun analyticsConsentFollowsTheChoice() = scope.runTest {
        val analyticsConsent = SettingsAnalyticsConsent(local)
        assertFalse(analyticsConsent.isGranted())

        setConsent(ConsentChoice(ConsentType.Analytics, granted = true))
        assertTrue(analyticsConsent.isGranted())

        setConsent(ConsentChoice(ConsentType.Analytics, granted = false))
        assertFalse(analyticsConsent.isGranted())
    }

    @Test
    fun analyticsConsentGivenOnAnOldTextDoesNotCount() = scope.runTest {
        local.update { settings ->
            settings.copy(
                consents = settings.consents.map {
                    if (it.type == ConsentType.Analytics) it.copy(granted = true, decidedAt = 1L, textVersion = 0) else it
                },
            )
        }

        assertFalse(SettingsAnalyticsConsent(local).isGranted())
    }
}

private class RecordingAnalyticsConsentObserver : AnalyticsConsentObserver {
    var calls = 0
    override suspend fun onAnalyticsConsentRevoked() {
        calls++
    }
}
