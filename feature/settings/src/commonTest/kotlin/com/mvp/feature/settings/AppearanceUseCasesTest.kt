package com.mvp.feature.settings

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.database.testing.createInMemoryDriver
import com.mvp.core.designsystem.AccentColor
import com.mvp.core.designsystem.Appearance
import com.mvp.core.designsystem.ThemeMode
import com.mvp.feature.settings.data.db.SettingsDatabase
import com.mvp.feature.settings.data.repository.SettingsRepositoryImpl
import com.mvp.feature.settings.data.session.SettingsUserSessionObserver
import com.mvp.feature.settings.data.source.SecureProfileLocalDataSource
import com.mvp.feature.settings.data.source.SqlDelightSettingsLocalDataSource
import com.mvp.feature.settings.domain.model.UserProfile
import com.mvp.feature.settings.domain.model.UserSettings
import com.mvp.feature.settings.domain.usecase.ObserveAppearanceUseCase
import com.mvp.feature.settings.domain.usecase.SetAccentColorUseCase
import com.mvp.feature.settings.domain.usecase.SetThemeModeUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Thème et couleur d'accent : choix, persistance, et conservation à la déconnexion (ADR 0015). */
class AppearanceUseCasesTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val driver = createInMemoryDriver(SettingsDatabase.Schema)
    private val storage = InMemorySecureStorage()

    private class Stack(
        val observe: ObserveAppearanceUseCase,
        val setThemeMode: SetThemeModeUseCase,
        val setAccent: SetAccentColorUseCase,
        val repository: SettingsRepositoryImpl,
        val session: SettingsUserSessionObserver,
    )

    /** Nouvelle instance sur la même base et le même stockage : équivaut à un redémarrage de l'app. */
    private fun newStack(): Stack {
        val settings = SqlDelightSettingsLocalDataSource(SettingsDatabase(driver), dispatcher)
        val profile = SecureProfileLocalDataSource(storage, legacy = settings)
        val repository = SettingsRepositoryImpl(settings, profile)
        return Stack(
            observe = ObserveAppearanceUseCase(repository, dispatcher),
            setThemeMode = SetThemeModeUseCase(repository, dispatcher),
            setAccent = SetAccentColorUseCase(repository, dispatcher),
            repository = repository,
            session = SettingsUserSessionObserver(settings, profile),
        )
    }

    private suspend fun Stack.current(): Appearance = observe(NoParams).first().getOrNull()!!

    @AfterTest
    fun closeDriver() = driver.close()

    @Test
    fun firstLaunchFollowsTheDeviceWithBlueAccent() = scope.runTest {
        assertEquals(Appearance(ThemeMode.System, AccentColor.Blue), newStack().current())
    }

    @Test
    fun themeAndAccentAreSetIndependentlyAndSurviveRestart() = scope.runTest {
        val stack = newStack()

        assertEquals(AppResult.Success(Unit), stack.setThemeMode(ThemeMode.Dark))
        assertEquals(AppResult.Success(Unit), stack.setAccent(AccentColor.Violet))
        assertEquals(Appearance(ThemeMode.Dark, AccentColor.Violet), stack.current())

        stack.setThemeMode(ThemeMode.Light)
        assertEquals(Appearance(ThemeMode.Light, AccentColor.Violet), newStack().current())
    }

    @Test
    fun appearanceIsEmittedOnlyWhenItChanges() = scope.runTest {
        val stack = newStack()
        val emitted = mutableListOf<Appearance>()
        val job = stack.observe(NoParams).onEach { emitted += it.getOrNull()!! }.launchIn(this)
        advanceUntilIdle()

        // Un autre réglage change : l'apparence ne réémet pas (pas de recomposition de toute l'app).
        stack.repository.setNotificationsEnabled(true)
        advanceUntilIdle()
        stack.setAccent(AccentColor.Orange)
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(Appearance.Default, Appearance(accent = AccentColor.Orange)), emitted)
    }

    @Test
    fun unknownStoredValueFallsBackToDefault() = scope.runTest {
        val queries = SettingsDatabase(driver).settingEntityQueries
        queries.upsert("appearance.theme_mode", "Sepia")
        queries.upsert("appearance.accent", "Pink")

        assertEquals(Appearance(ThemeMode.System, AccentColor.Pink), newStack().current())
    }

    @Test
    fun logoutKeepsTheAppearanceAndWipesTheRest() = scope.runTest {
        val stack = newStack()
        stack.repository.updateProfile(UserProfile("Youssef", "y@example.com"))
        stack.repository.setNotificationsEnabled(true)
        stack.setThemeMode(ThemeMode.Dark)
        stack.setAccent(AccentColor.Green)

        assertEquals(AppResult.Success(Unit), stack.session.clearUserData())
        assertEquals(Appearance(ThemeMode.Dark, AccentColor.Green), stack.current())
        stack.session.onLogout()

        val expected = UserSettings.initial().copy(appearance = Appearance(ThemeMode.Dark, AccentColor.Green))
        assertEquals(expected, newStack().repository.observeSettings().first())
        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun logoutNeverShowsTheDefaultAppearanceInBetween() = scope.runTest {
        val stack = newStack()
        stack.setThemeMode(ThemeMode.Dark)
        stack.setAccent(AccentColor.Pink)
        advanceUntilIdle()
        val emitted = mutableListOf<Appearance>()
        val job = stack.observe(NoParams).onEach { emitted += it.getOrNull()!! }.launchIn(this)
        advanceUntilIdle()

        stack.session.clearUserData()
        advanceUntilIdle()
        stack.session.onLogout()
        advanceUntilIdle()
        job.cancel()

        // Pas de passage par le thème de l'appareil et l'accent bleu pendant la déconnexion.
        assertEquals(listOf(Appearance(ThemeMode.Dark, AccentColor.Pink)), emitted)
        val keys = SettingsDatabase(driver).settingEntityQueries.selectAll().executeAsList().map { it.setting_key }.toSet()
        assertEquals(setOf("appearance.theme_mode", "appearance.accent"), keys)
    }

    @Test
    fun loginOfAnotherAccountKeepsTheAppearance() = scope.runTest {
        val stack = newStack()
        stack.setAccent(AccentColor.Pink)

        stack.session.onLogin(UserAccount(id = "demo:a@example.com", displayName = "A", email = "a@example.com"))

        assertEquals(AccentColor.Pink, stack.current().accent)
    }
}
