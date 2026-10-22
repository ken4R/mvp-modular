package com.mvp.feature.settings

import com.mvp.core.database.testing.createInMemoryDriver
import com.mvp.feature.settings.data.db.SettingsDatabase
import com.mvp.feature.settings.data.source.SqlDelightSettingsLocalDataSource
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.model.ConsentTexts
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
import kotlin.test.assertNull

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
                consents = settings.consents.map { if (it.type == ConsentType.Marketing) it.grantedNow() else it },
                appLockEnabled = true,
            )
        }

        val restored = newDataSource().observe().first()

        // Le profil n'est plus écrit en clair dans la base : il est dans le stockage chiffré.
        assertEquals(UserSettings.initial().profile, restored.profile)
        assertEquals(true, restored.notificationsEnabled)
        assertEquals(true, restored.appLockEnabled)
        assertEquals(
            listOf(
                Consent(ConsentType.Analytics, granted = false),
                Consent(ConsentType.Marketing, granted = true, decidedAt = DECIDED_AT, textVersion = 1),
                Consent(ConsentType.Personalization, granted = false),
            ),
            restored.consents,
        )
    }

    @Test
    fun successiveUpdatesBuildOnEachOther() = scope.runTest {
        val source = newDataSource()

        source.update { it.copy(notificationsEnabled = true) }
        source.setConsentGranted(ConsentType.Analytics)

        val restored = newDataSource().observe().first()
        assertEquals(true, restored.notificationsEnabled)
        assertEquals(true, restored.consents.first { it.type == ConsentType.Analytics }.granted)
    }

    @Test
    fun clearRestoresFirstLaunchSettings() = scope.runTest {
        val source = newDataSource()
        source.update { it.copy(notificationsEnabled = true) }
        source.setConsentGranted(ConsentType.Marketing)

        source.clear()

        assertEquals(UserSettings.initial(), newDataSource().observe().first())
    }

    @Test
    fun legacyProfileIsReadThenDeleted() = scope.runTest {
        // Profil écrit en clair par la version précédente (PR #7).
        val queries = SettingsDatabase(driver).settingEntityQueries
        queries.upsert("profile.display_name", "Youssef")
        queries.upsert("profile.email", "y@example.com")
        queries.upsert("notifications.enabled", "true")
        val source = newDataSource()

        assertEquals(UserProfile("Youssef", "y@example.com"), source.readLegacyProfile())

        source.deleteLegacyProfile()

        assertNull(source.readLegacyProfile())
        assertEquals(listOf("notifications.enabled"), queries.selectAll().executeAsList().map { it.setting_key })
    }

    @Test
    fun unreadableStoredValueFallsBackToDefault() = scope.runTest {
        SettingsDatabase(driver).settingEntityQueries.upsert("notifications.enabled", "peut-être")

        assertEquals(false, newDataSource().observe().first().notificationsEnabled)
    }

    @Test
    fun undatedConsentFromAnOlderVersionIsAskedAgain() = scope.runTest {
        // Accord enregistré avant que les choix ne soient datés (PR #6 à #13).
        SettingsDatabase(driver).settingEntityQueries.upsert("consent.Analytics", "true")

        assertEquals(Consent(ConsentType.Analytics, granted = false), newDataSource().observe().first().consents.first())
    }

    @Test
    fun consentGivenOnAnOldTextIsAskedAgain() = scope.runTest {
        val queries = SettingsDatabase(driver).settingEntityQueries
        queries.upsert("consent.Analytics", "true")
        queries.upsert("consent.Analytics.decided_at", DECIDED_AT.toString())
        queries.upsert("consent.Analytics.text_version", (ConsentTexts.of(ConsentType.Analytics).version - 1).toString())

        assertEquals(Consent(ConsentType.Analytics, granted = false), newDataSource().observe().first().consents.first())
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

private const val DECIDED_AT = 1_761_000_000_000L

private fun Consent.grantedNow() = copy(granted = true, decidedAt = DECIDED_AT, textVersion = ConsentTexts.of(type).version)

private fun SqlDelightSettingsLocalDataSource.setConsentGranted(type: ConsentType) = update { settings ->
    settings.copy(consents = settings.consents.map { if (it.type == type) it.grantedNow() else it })
}
