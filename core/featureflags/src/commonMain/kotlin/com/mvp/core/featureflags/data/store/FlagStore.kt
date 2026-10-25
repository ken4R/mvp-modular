package com.mvp.core.featureflags.data.store

import com.mvp.core.files.PrivateFileStore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cache de la dernière réponse distante valide et réglages locaux du menu debug, dans un dossier privé hors
 * sauvegardes (core:files). Ce ne sont pas des données de l'utilisateur : rien n'est effacé à la déconnexion.
 * Les opérations sont bloquantes : à appeler depuis un dispatcher d'entrées / sorties.
 */
internal class FlagStore(private val files: PrivateFileStore) {

    private val json = Json { ignoreUnknownKeys = true }

    fun readCache(): RemoteFlagCache? = read(CACHE) { RemoteFlagCache.serializer() }

    fun writeCache(cache: RemoteFlagCache) =
        files.write(CACHE, json.encodeToString(RemoteFlagCache.serializer(), cache).encodeToByteArray())

    fun readOverrides(): Map<String, Boolean> = read(OVERRIDES) { FlagOverrides.serializer() }?.values.orEmpty()

    fun writeOverrides(values: Map<String, Boolean>) {
        if (values.isEmpty()) {
            files.delete(OVERRIDES)
        } else {
            files.write(OVERRIDES, json.encodeToString(FlagOverrides.serializer(), FlagOverrides(values)).encodeToByteArray())
        }
    }

    // Un fichier illisible (coupé, ancienne version) est effacé : on repart des valeurs par défaut.
    private fun <T> read(name: String, serializer: () -> KSerializer<T>): T? {
        val bytes = files.read(name) ?: return null
        return runCatching { json.decodeFromString(serializer(), bytes.decodeToString()) }
            .onFailure { files.delete(name) }
            .getOrNull()
    }

    private companion object {
        const val CACHE = "remote_cache"
        const val OVERRIDES = "overrides"
    }
}

@Serializable
internal data class RemoteFlagCache(val fetchedAt: Long, val flags: Map<String, Boolean>)

@Serializable
private data class FlagOverrides(val values: Map<String, Boolean>)
