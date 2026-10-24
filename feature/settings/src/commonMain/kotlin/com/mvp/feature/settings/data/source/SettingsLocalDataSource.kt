package com.mvp.feature.settings.data.source

import com.mvp.feature.settings.domain.model.UserProfile
import com.mvp.feature.settings.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Stockage local des réglages. L'app utilise [SqlDelightSettingsLocalDataSource] (SQLite,
 * persistant) ; l'implémentation en mémoire sert aux tests des use cases.
 *
 * Le profil n'est pas gardé ici mais dans le stockage chiffré ([ProfileLocalDataSource]) :
 * le champ `profile` des réglages lus ou écrits ici est ignoré.
 */
interface SettingsLocalDataSource {
    fun observe(): Flow<UserSettings>
    fun update(transform: (UserSettings) -> UserSettings)

    /** Remet tous les réglages à leur valeur de premier lancement. */
    fun clear()

    /** Comme [clear], mais garde l'apparence (thème, accent), en une seule écriture : jamais d'état intermédiaire. */
    fun clearExceptAppearance()

    /** Profil enregistré par une ancienne version de l'app (en clair), à migrer vers le stockage chiffré. */
    fun readLegacyProfile(): UserProfile? = null
    fun deleteLegacyProfile() {}
}

internal class InMemorySettingsLocalDataSource(
    initial: UserSettings = UserSettings.initial(),
) : SettingsLocalDataSource {

    private val settings = MutableStateFlow(initial)

    override fun observe(): Flow<UserSettings> = settings.asStateFlow()

    override fun update(transform: (UserSettings) -> UserSettings) = settings.update(transform)

    override fun clear() {
        settings.value = UserSettings.initial()
    }

    override fun clearExceptAppearance() = settings.update { UserSettings.initial().copy(appearance = it.appearance) }
}
