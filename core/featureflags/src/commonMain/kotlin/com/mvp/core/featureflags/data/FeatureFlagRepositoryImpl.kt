package com.mvp.core.featureflags.data

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.featureflags.data.remote.RemoteFlagSource
import com.mvp.core.featureflags.data.store.FlagStore
import com.mvp.core.featureflags.data.store.RemoteFlagCache
import com.mvp.core.featureflags.domain.model.FlagInputs
import com.mvp.core.featureflags.domain.repository.FeatureFlagRepository
import com.mvp.core.network.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Flags en mémoire, chargés une fois depuis le cache disque. En release, les réglages locaux ne sont ni lus ni
 * écrits (les use cases les refusent déjà) : un fichier laissé par un build de debug n'a aucun effet.
 * Les fichiers sont lus et écrits sur le dispatcher des use cases (entrées / sorties).
 */
internal class FeatureFlagRepositoryImpl(
    private val store: FlagStore,
    private val remote: RemoteFlagSource,
    override val isDevBuild: Boolean,
    private val now: () -> Long,
) : FeatureFlagRepository {

    private val mutex = Mutex()
    private val inputs = MutableStateFlow<FlagInputs?>(null)
    private var fetchedAt: Long? = null

    override fun observeInputs(): Flow<FlagInputs> = inputs.onStart { ensureLoaded() }.filterNotNull()

    override suspend fun lastFetchedAt(): Long? {
        ensureLoaded()
        return fetchedAt
    }

    override suspend fun fetchRemote(): AppResult<Unit> {
        ensureLoaded()
        return when (val result = safeApiCall { remote.fetch() }) {
            is AppResult.Failure -> result
            is AppResult.Success -> mutex.withLock {
                val cache = RemoteFlagCache(fetchedAt = now(), flags = result.data)
                store.writeCache(cache)
                fetchedAt = cache.fetchedAt
                inputs.update { it?.copy(remote = cache.flags) }
                AppResult.Success(Unit)
            }
        }
    }

    override suspend fun setOverride(key: String, enabled: Boolean?) = editOverrides { current ->
        if (enabled == null) current - key else current + (key to enabled)
    }

    override suspend fun clearOverrides() = editOverrides { emptyMap() }

    private suspend fun editOverrides(transform: (Map<String, Boolean>) -> Map<String, Boolean>) {
        if (!isDevBuild) return
        ensureLoaded()
        mutex.withLock {
            val updated = transform(inputs.value?.overrides.orEmpty())
            store.writeOverrides(updated)
            inputs.update { it?.copy(overrides = updated) }
        }
    }

    private suspend fun ensureLoaded() {
        if (inputs.value != null) return
        mutex.withLock {
            if (inputs.value != null) return
            val cache = store.readCache()
            fetchedAt = cache?.fetchedAt
            inputs.value = FlagInputs(
                remote = cache?.flags.orEmpty(),
                overrides = if (isDevBuild) store.readOverrides() else emptyMap(),
            )
        }
    }
}
