package com.mvp.feature.settings.data.source

import com.mvp.core.securestorage.SecureStorage
import com.mvp.feature.settings.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Profil de l'utilisateur (nom, e-mail) : donnée personnelle, gardée à part des autres réglages. */
interface ProfileLocalDataSource {
    fun observe(): Flow<UserProfile>
    fun current(): UserProfile
    fun save(profile: UserProfile)
    fun clear()
}

/**
 * Profil dans le stockage chiffré (Keystore / Keychain), lu au premier accès puis gardé en mémoire.
 * Au premier lancement après la mise à jour, le profil laissé en clair dans la base des réglages
 * par l'ancienne version est recopié ici, puis effacé de la base.
 */
internal class SecureProfileLocalDataSource(
    private val storage: SecureStorage,
    private val legacy: SettingsLocalDataSource,
) : ProfileLocalDataSource {

    private val profile by lazy { MutableStateFlow(read() ?: migrateLegacyProfile()) }

    // Première lecture au moment de la collecte (sur le dispatcher io), pas à la création du Flow.
    override fun observe(): Flow<UserProfile> = flow { emitAll(profile) }

    override fun current(): UserProfile = profile.value

    override fun save(profile: UserProfile) {
        write(profile)
        this.profile.value = profile
    }

    override fun clear() {
        storage.remove(KEY_DISPLAY_NAME)
        storage.remove(KEY_EMAIL)
        profile.value = EMPTY
    }

    private fun read(): UserProfile? {
        val displayName = storage.get(KEY_DISPLAY_NAME) ?: return null
        return UserProfile(displayName = displayName, email = storage.get(KEY_EMAIL).orEmpty())
    }

    private fun write(profile: UserProfile) {
        storage.put(KEY_EMAIL, profile.email)
        storage.put(KEY_DISPLAY_NAME, profile.displayName)
    }

    // Écriture chiffrée d'abord, effacement de l'ancienne copie ensuite : rien n'est perdu si l'app s'arrête entre les deux.
    private fun migrateLegacyProfile(): UserProfile {
        val legacyProfile = legacy.readLegacyProfile()?.takeIf { it.displayName.isNotEmpty() }
        legacyProfile?.let(::write)
        legacy.deleteLegacyProfile()
        return legacyProfile ?: EMPTY
    }

    private companion object {
        // Les clés sont stockées sur l'appareil : ne pas les renommer sans migration.
        const val KEY_DISPLAY_NAME = "settings.profile.display_name"
        const val KEY_EMAIL = "settings.profile.email"
        val EMPTY = UserProfile(displayName = "", email = "")
    }
}
