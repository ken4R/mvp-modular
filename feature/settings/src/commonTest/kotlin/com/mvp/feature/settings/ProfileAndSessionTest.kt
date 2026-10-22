package com.mvp.feature.settings

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.core.database.testing.createInMemoryDriver
import com.mvp.feature.settings.data.db.SettingsDatabase
import com.mvp.feature.settings.data.repository.SettingsRepositoryImpl
import com.mvp.feature.settings.data.session.SettingsUserSessionObserver
import com.mvp.feature.settings.data.source.SecureProfileLocalDataSource
import com.mvp.feature.settings.data.source.SqlDelightSettingsLocalDataSource
import com.mvp.feature.settings.domain.model.UserProfile
import com.mvp.feature.settings.domain.model.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Profil dans le stockage chiffré, migration depuis la base, et réaction à la connexion / déconnexion. */
class ProfileAndSessionTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val driver = createInMemoryDriver(SettingsDatabase.Schema)
    private val storage = InMemorySecureStorage()

    private class Stack(val repository: SettingsRepositoryImpl, val observer: SettingsUserSessionObserver)

    /** Nouvelle instance sur la même base et le même stockage : équivaut à un redémarrage de l'app. */
    private fun newStack(): Stack {
        val settings = SqlDelightSettingsLocalDataSource(SettingsDatabase(driver), dispatcher)
        val profile = SecureProfileLocalDataSource(storage, legacy = settings)
        return Stack(SettingsRepositoryImpl(settings, profile), SettingsUserSessionObserver(settings, profile))
    }

    private suspend fun Stack.current(): UserSettings = repository.observeSettings().first()

    private val account = UserAccount(id = "demo:y@example.com", displayName = "Youssef", email = "y@example.com")

    @AfterTest
    fun closeDriver() = driver.close()

    @Test
    fun profileIsStoredEncryptedAndSurvivesRestart() = scope.runTest {
        newStack().repository.updateProfile(UserProfile("Youssef", "y@example.com"))

        assertEquals(UserProfile("Youssef", "y@example.com"), newStack().current().profile)
        assertTrue(storage.values.containsValue("y@example.com"))
        assertTrue(SettingsDatabase(driver).settingEntityQueries.selectAll().executeAsList().none { it.setting_value == "y@example.com" })
    }

    @Test
    fun legacyProfileIsMovedToEncryptedStorage() = scope.runTest {
        val queries = SettingsDatabase(driver).settingEntityQueries
        queries.upsert("profile.display_name", "Youssef")
        queries.upsert("profile.email", "y@example.com")

        assertEquals(UserProfile("Youssef", "y@example.com"), newStack().current().profile)

        assertTrue(queries.selectAll().executeAsList().none { it.setting_key.startsWith("profile.") })
        assertEquals(UserProfile("Youssef", "y@example.com"), newStack().current().profile)
    }

    @Test
    fun emptyLegacyProfileIsDroppedWithoutMigration() = scope.runTest {
        val queries = SettingsDatabase(driver).settingEntityQueries
        queries.upsert("profile.display_name", "")
        queries.upsert("profile.email", "")

        assertEquals(UserSettings.initial().profile, newStack().current().profile)
        assertTrue(storage.values.isEmpty())
        assertTrue(queries.selectAll().executeAsList().isEmpty())
    }

    @Test
    fun loginPrefillsEmptyProfile() = scope.runTest {
        val stack = newStack()

        assertEquals(AppResult.Success(Unit), stack.observer.onLogin(account))

        assertEquals(UserProfile("Youssef", "y@example.com"), stack.current().profile)
    }

    @Test
    fun loginKeepsProfileAlreadyFilled() = scope.runTest {
        val stack = newStack()
        stack.repository.updateProfile(UserProfile("Youssef B.", ""))

        stack.observer.onLogin(account)

        assertEquals(UserProfile("Youssef B.", ""), stack.current().profile)
    }

    @Test
    fun logoutWipesProfileAndSettings() = scope.runTest {
        val stack = newStack()
        stack.repository.updateProfile(UserProfile("Youssef", "y@example.com"))
        stack.repository.setNotificationsEnabled(true)

        assertEquals(AppResult.Success(Unit), stack.observer.clearUserData())
        stack.observer.onLogout()

        assertEquals(UserSettings.initial(), stack.current())
        assertEquals(UserSettings.initial(), newStack().current())
        assertTrue(storage.values.isEmpty())
        assertNull(SettingsDatabase(driver).settingEntityQueries.selectAll().executeAsList().firstOrNull())
    }

    @Test
    fun appLockStaysOnUntilTheLogoutIsComplete() = scope.runTest {
        val stack = newStack()
        stack.repository.updateProfile(UserProfile("Youssef", "y@example.com"))
        stack.repository.setNotificationsEnabled(true)
        stack.repository.setAppLockEnabled(true)

        // Session encore valide, autres features peut-être pas encore effacées : le verrou tient.
        assertEquals(AppResult.Success(Unit), stack.observer.clearUserData())
        assertEquals(UserSettings.initial().copy(appLockEnabled = true), stack.current())

        stack.observer.onLogout()
        assertEquals(UserSettings.initial(), stack.current())
    }

    @Test
    fun appLockKeptByAnAccountChangeIsDroppedAtLogin() = scope.runTest {
        val stack = newStack()
        stack.repository.updateProfile(UserProfile("Ancien compte", ""))
        stack.repository.setAppLockEnabled(true)

        stack.observer.clearUserData()
        stack.observer.onLogin(account)

        assertEquals(false, stack.current().appLockEnabled)
        assertEquals(UserProfile("Youssef", "y@example.com"), stack.current().profile)
    }

    @Test
    fun sameAccountLoggingBackInKeepsItsAppLock() = scope.runTest {
        val stack = newStack()
        stack.repository.updateProfile(UserProfile("Youssef", "y@example.com"))
        stack.repository.setAppLockEnabled(true)

        stack.observer.onLogin(account)

        assertEquals(true, stack.current().appLockEnabled)
    }
}
