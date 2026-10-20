package com.mvp.feature.settings

import com.mvp.core.database.testing.createInMemoryDriver
import com.mvp.feature.settings.data.db.SettingsDatabase
import com.mvp.feature.settings.data.source.SqlDelightSettingsLocalDataSource
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.model.ConsentType
import com.mvp.feature.settings.domain.model.UserProfile
import com.mvp.feature.settings.domain.model.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SqlDelightSettingsLocalDataSourceTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val driver = createInMemoryDriver(SettingsDatabase.Schema)

    /** Une nouvelle instance sur la même base équivaut à un redémarrage de l'app. */
    private fun newDataSource() = SqlDelightSettingsLocalDataSource(SettingsDatabase(driver), dispatcher)

    @AfterTest
    fun closeDriver() = driver.close()

    @Test
    fun emptyDatabaseGivesFirstLaunchSettings() = scope.runTest {
        assertEquals(UserSettings.initial(), newDataSource().observe().first())
    }

    @Test
    fun settingsSurviveRestart() = scope.runTest {
        newDataSource().update { settings ->
            settings.copy(
                profile = UserProfile(displayName = "Youssef", email = "y@example.com"),
                notificationsEnabled = true,
                consents = settings.consents.map { if (it.type == ConsentType.Marketing) it.copy(granted = true) else it },
            )
        }

        val restored = newDataSource().observe().first()

        assertEquals(UserProfile("Youssef", "y@example.com"), restored.profile)
        assertEquals(true, restored.notificationsEnabled)
        assertEquals(
            listOf(
                Consent(ConsentType.Analytics, granted = false),
                Consent(ConsentType.Marketing, granted = true),
                Consent(ConsentType.Personalization, granted = false),
            ),
            restored.consents,
        )
    }

    @Test
    fun successiveUpdatesBuildOnEachOther() = scope.runTest {
        val source = newDataSource()

        source.update { it.copy(notificationsEnabled = true) }
        source.update { it.copy(profile = UserProfile("Youssef", "")) }

        val restored = newDataSource().observe().first()
        assertEquals(true, restored.notificationsEnabled)
        assertEquals("Youssef", restored.profile.displayName)
    }

    @Test
    fun unreadableStoredValueFallsBackToDefault() = scope.runTest {
        SettingsDatabase(driver).settingEntityQueries.upsert("notifications.enabled", "peut-être")

        assertEquals(false, newDataSource().observe().first().notificationsEnabled)
    }

    @Test
    fun failedTransformLeavesSettingsUnchanged() = scope.runTest {
        val source = newDataSource()

        assertFailsWith<IllegalStateException> {
            source.update { error("échec pendant la mise à jour") }
        }

        assertEquals(UserSettings.initial(), newDataSource().observe().first())
    }
}
