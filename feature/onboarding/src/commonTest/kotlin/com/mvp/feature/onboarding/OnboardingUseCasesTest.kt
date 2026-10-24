package com.mvp.feature.onboarding

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.database.testing.createInMemoryDriver
import com.mvp.feature.onboarding.data.db.OnboardingDatabase
import com.mvp.feature.onboarding.data.repository.OnboardingRepositoryImpl
import com.mvp.feature.onboarding.data.source.InMemoryOnboardingLocalDataSource
import com.mvp.feature.onboarding.data.source.OnboardingLocalDataSource
import com.mvp.feature.onboarding.data.source.SqlDelightOnboardingLocalDataSource
import com.mvp.feature.onboarding.domain.model.ONBOARDING_VERSION
import com.mvp.feature.onboarding.domain.model.OnboardingDevice
import com.mvp.feature.onboarding.domain.model.OnboardingStep
import com.mvp.feature.onboarding.domain.model.OnboardingTexts
import com.mvp.feature.onboarding.domain.repository.OnboardingRepository
import com.mvp.feature.onboarding.domain.usecase.CompleteOnboardingUseCase
import com.mvp.feature.onboarding.domain.usecase.GetOnboardingStepsUseCase
import com.mvp.feature.onboarding.domain.usecase.ObserveOnboardingRequiredUseCase
import com.mvp.feature.onboarding.domain.usecase.RestartOnboardingUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Présentation du premier lancement : affichée une fois par appareil, persistante, remontrable (ADR 0016). */
class OnboardingUseCasesTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val driver = createInMemoryDriver(OnboardingDatabase.Schema)

    private class Stack(
        val observe: ObserveOnboardingRequiredUseCase,
        val complete: CompleteOnboardingUseCase,
        val restart: RestartOnboardingUseCase,
    )

    private fun stackOf(repository: OnboardingRepository) = Stack(
        observe = ObserveOnboardingRequiredUseCase(repository, dispatcher),
        complete = CompleteOnboardingUseCase(repository, dispatcher),
        restart = RestartOnboardingUseCase(repository, dispatcher),
    )

    /** Nouvelle instance sur la même base : équivaut à un redémarrage de l'app. */
    private fun newStack() =
        stackOf(OnboardingRepositoryImpl(SqlDelightOnboardingLocalDataSource(OnboardingDatabase(driver), dispatcher)))

    private suspend fun Stack.required(): Boolean? = observe(NoParams).first().getOrNull()

    @AfterTest
    fun closeDriver() = driver.close()

    @Test
    fun requiredOnFirstLaunch() = scope.runTest {
        assertEquals(true, newStack().required())
    }

    @Test
    fun completedOnboardingIsNotShownAgainAfterRestart() = scope.runTest {
        assertIs<AppResult.Success<Unit>>(newStack().complete(NoParams))

        assertEquals(false, newStack().required())
    }

    @Test
    fun restartShowsItAgain() = scope.runTest {
        val stack = newStack()
        stack.complete(NoParams)

        assertIs<AppResult.Success<Unit>>(stack.restart(NoParams))

        assertEquals(true, newStack().required())
    }

    @Test
    fun olderVersionIsShownAgain() = scope.runTest {
        val stack = stackOf(OnboardingRepositoryImpl(InMemoryOnboardingLocalDataSource(initial = ONBOARDING_VERSION - 1)))

        assertEquals(true, stack.required())
        stack.complete(NoParams)
        assertEquals(false, stack.required())
    }

    @Test
    fun unreadableStoredValueIsReadAsNeverSeen() = scope.runTest {
        OnboardingDatabase(driver).onboardingStateEntityQueries.upsert("completed_version", "pas un nombre")

        assertEquals(true, newStack().required())
    }

    @Test
    fun emitsOnlyWhenTheAnswerChanges() = scope.runTest {
        val local = InMemoryOnboardingLocalDataSource()
        val stack = stackOf(OnboardingRepositoryImpl(local))
        val emitted = mutableListOf<Boolean?>()
        val job = stack.observe(NoParams).onEach { emitted += it.getOrNull() }.launchIn(this)
        advanceUntilIdle()

        // Version déjà vue réécrite, puis une version plus récente : toujours « pas besoin ».
        stack.complete(NoParams)
        advanceUntilIdle()
        stack.complete(NoParams)
        local.setCompletedVersion(ONBOARDING_VERSION + 1)
        advanceUntilIdle()
        stack.restart(NoParams)
        advanceUntilIdle()

        assertEquals(listOf<Boolean?>(true, false, true), emitted)
        job.cancel()
    }

    @Test
    fun storageErrorIsReturnedAsFailure() = scope.runTest {
        val failing = object : OnboardingLocalDataSource {
            override fun observeCompletedVersion(): Flow<Int?> = flow { error("base illisible") }
            override fun setCompletedVersion(version: Int?) = error("disque plein")
        }
        val stack = stackOf(OnboardingRepositoryImpl(failing))

        assertIs<AppResult.Failure>(stack.observe(NoParams).first())
        assertIs<AppResult.Failure>(stack.complete(NoParams))
    }

    @Test
    fun appLockStepOnlyWhenTheDeviceCanUseIt() = scope.runTest {
        val getSteps = GetOnboardingStepsUseCase(dispatcher)

        val withLock = getSteps(OnboardingDevice(appLockAvailable = true)).getOrNull()
        val withoutLock = getSteps(OnboardingDevice(appLockAvailable = false)).getOrNull()

        assertEquals(OnboardingStep.entries.toList(), withLock)
        assertEquals(
            listOf(OnboardingStep.Welcome, OnboardingStep.Notifications, OnboardingStep.Location, OnboardingStep.Analytics,
                OnboardingStep.CrashReports,
            ),
            withoutLock,
        )
    }

    @Test
    fun everyStepHasATextAndOnlyWelcomeHasNothingToDecline() {
        OnboardingStep.entries.forEach { step ->
            val text = OnboardingTexts.of(step)
            assertTrue(text.title.isNotBlank() && text.body.isNotBlank() && text.acceptLabel.isNotBlank(), "$step")
            if (step == OnboardingStep.Welcome) assertNull(text.declineLabel) else assertNotNull(text.declineLabel, "$step")
        }
    }
}
