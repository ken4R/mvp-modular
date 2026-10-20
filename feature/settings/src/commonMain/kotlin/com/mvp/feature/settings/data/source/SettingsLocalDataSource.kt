package com.mvp.feature.settings.data.source

import com.mvp.feature.settings.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Stockage local des réglages. L'implémentation en mémoire suffit pour le prototype ;
 * elle pourra être remplacée sans toucher au domaine : DataStore / NSUserDefaults pour les
 * préférences (notifications, consentements), mais stockage chiffré (Keystore / Keychain)
 * pour le profil, qui contient des données personnelles.
 */
interface SettingsLocalDataSource {
    fun observe(): Flow<UserSettings>
    fun update(transform: (UserSettings) -> UserSettings)
}

internal class InMemorySettingsLocalDataSource(
    initial: UserSettings = UserSettings.initial(),
) : SettingsLocalDataSource {

    private val settings = MutableStateFlow(initial)

    override fun observe(): Flow<UserSettings> = settings.asStateFlow()

    override fun update(transform: (UserSettings) -> UserSettings) = settings.update(transform)
}
