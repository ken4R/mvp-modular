package com.mvp.feature.settings.data.source

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mvp.feature.settings.data.db.SettingsDatabase
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.model.ConsentType
import com.mvp.feature.settings.domain.model.UserProfile
import com.mvp.feature.settings.domain.model.UserSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Réglages stockés dans SQLite : ils survivent au redémarrage de l'app. Le profil n'y est plus. */
internal class SqlDelightSettingsLocalDataSource(
    database: SettingsDatabase,
    private val dispatcher: CoroutineDispatcher,
) : SettingsLocalDataSource {

    private val queries = database.settingEntityQueries

    override fun observe(): Flow<UserSettings> =
        queries.selectAll(::Pair).asFlow().mapToList(dispatcher).map { it.toMap().toSettings() }
            // Chaque update réécrit toutes les clés : ne réémettre que si les réglages changent.
            .distinctUntilChanged()

    // Lecture, transformation et écriture dans une même transaction : pas de mise à jour perdue.
    override fun update(transform: (UserSettings) -> UserSettings) = queries.transaction {
        val current = queries.selectAll(::Pair).executeAsList().toMap().toSettings()
        transform(current).toEntries().forEach { (key, value) -> queries.upsert(key, value) }
    }

    override fun clear() {
        queries.deleteAll()
    }

    // Avant le passage au stockage chiffré, le profil était rangé ici, en clair.
    override fun readLegacyProfile(): UserProfile? {
        val stored = queries.selectAll(::Pair).executeAsList().toMap()
        val displayName = stored[KEY_LEGACY_DISPLAY_NAME] ?: return null
        return UserProfile(displayName = displayName, email = stored[KEY_LEGACY_EMAIL].orEmpty())
    }

    override fun deleteLegacyProfile() = queries.transaction {
        queries.deleteByKey(KEY_LEGACY_DISPLAY_NAME)
        queries.deleteByKey(KEY_LEGACY_EMAIL)
    }
}

// Les clés sont stockées sur l'appareil : ne pas les renommer sans migration.
private const val KEY_LEGACY_DISPLAY_NAME = "profile.display_name"
private const val KEY_LEGACY_EMAIL = "profile.email"
private const val KEY_NOTIFICATIONS = "notifications.enabled"
private fun consentKey(type: ConsentType) = "consent.${type.name}"

private fun Map<String, String>.toSettings(): UserSettings {
    val defaults = UserSettings.initial()
    return UserSettings(
        profile = defaults.profile,
        notificationsEnabled = this[KEY_NOTIFICATIONS]?.toBooleanStrictOrNull() ?: defaults.notificationsEnabled,
        consents = defaults.consents.map { default ->
            Consent(default.type, this[consentKey(default.type)]?.toBooleanStrictOrNull() ?: default.granted)
        },
    )
}

private fun UserSettings.toEntries(): List<Pair<String, String>> = buildList {
    add(KEY_NOTIFICATIONS to notificationsEnabled.toString())
    consents.forEach { add(consentKey(it.type) to it.granted.toString()) }
}
