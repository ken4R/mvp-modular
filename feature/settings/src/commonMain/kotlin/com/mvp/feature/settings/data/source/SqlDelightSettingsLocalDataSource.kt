package com.mvp.feature.settings.data.source

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mvp.core.designsystem.AccentColor
import com.mvp.core.designsystem.Appearance
import com.mvp.core.designsystem.ThemeMode
import com.mvp.feature.settings.data.db.SettingsDatabase
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.model.ConsentTexts
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

    // Seules les clés d'apparence sont réécrites (et seulement si elles ne valent pas le défaut) : la table reste
    // sinon vide, sans dates de consentement ni autre trace de la session.
    override fun clearExceptAppearance() = queries.transaction {
        val appearance = queries.selectAll(::Pair).executeAsList().toMap().toSettings().appearance
        queries.deleteAll()
        if (appearance != UserSettings.initial().appearance) {
            queries.upsert(KEY_THEME_MODE, appearance.themeMode.name)
            queries.upsert(KEY_ACCENT, appearance.accent.name)
        }
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
private const val KEY_APP_LOCK = "app_lock.enabled"
// Valeur : nom de l'entrée de l'enum (ne pas renommer les entrées sans migration).
private const val KEY_THEME_MODE = "appearance.theme_mode"
private const val KEY_ACCENT = "appearance.accent"
private fun consentKey(type: ConsentType) = "consent.${type.name}"
private fun consentDecidedAtKey(type: ConsentType) = "consent.${type.name}.decided_at"
private fun consentTextVersionKey(type: ConsentType) = "consent.${type.name}.text_version"

private fun Map<String, String>.toSettings(): UserSettings {
    val defaults = UserSettings.initial()
    return UserSettings(
        profile = defaults.profile,
        notificationsEnabled = this[KEY_NOTIFICATIONS]?.toBooleanStrictOrNull() ?: defaults.notificationsEnabled,
        consents = defaults.consents.map { default -> readConsent(default) },
        appLockEnabled = this[KEY_APP_LOCK]?.toBooleanStrictOrNull() ?: defaults.appLockEnabled,
        // Une valeur inconnue (entrée retirée dans une version suivante) revient à la valeur par défaut.
        appearance = Appearance(
            themeMode = ThemeMode.entries.firstOrNull { it.name == this[KEY_THEME_MODE] } ?: defaults.appearance.themeMode,
            accent = AccentColor.entries.firstOrNull { it.name == this[KEY_ACCENT] } ?: defaults.appearance.accent,
        ),
    )
}

/**
 * Un choix fait sur une autre version du texte que l'actuelle (ou avant que les choix ne soient datés)
 * est lu comme jamais fait : refusé, et redemandé à l'utilisateur.
 */
private fun Map<String, String>.readConsent(default: Consent): Consent {
    val type = default.type
    val granted = this[consentKey(type)]?.toBooleanStrictOrNull() ?: return default
    val textVersion = this[consentTextVersionKey(type)]?.toIntOrNull()
    val decidedAt = this[consentDecidedAtKey(type)]?.toLongOrNull()
    if (textVersion != ConsentTexts.of(type).version || decidedAt == null) return default
    return Consent(type, granted, decidedAt = decidedAt, textVersion = textVersion)
}

private fun UserSettings.toEntries(): List<Pair<String, String>> = buildList {
    add(KEY_NOTIFICATIONS to notificationsEnabled.toString())
    consents.forEach { consent ->
        add(consentKey(consent.type) to consent.granted.toString())
        consent.decidedAt?.let { add(consentDecidedAtKey(consent.type) to it.toString()) }
        consent.textVersion?.let { add(consentTextVersionKey(consent.type) to it.toString()) }
    }
    add(KEY_APP_LOCK to appLockEnabled.toString())
    add(KEY_THEME_MODE to appearance.themeMode.name)
    add(KEY_ACCENT to appearance.accent.name)
}
