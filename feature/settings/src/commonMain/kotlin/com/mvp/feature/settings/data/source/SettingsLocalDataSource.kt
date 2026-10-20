package com.mvp.feature.settings.data.source

import com.mvp.feature.settings.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Stockage local des réglages. L'app utilise [SqlDelightSettingsLocalDataSource] (SQLite,
 * persistant) ; l'implémentation en mémoire sert aux tests des use cases.
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
