package com.mvp.feature.focus.data.repository

import com.mvp.core.securestorage.SecureStorage
import com.mvp.feature.focus.domain.model.FocusPhase
import com.mvp.feature.focus.domain.model.FocusSession
import com.mvp.feature.focus.domain.repository.FocusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Session gardée dans le stockage chiffré (Keystore / Keychain) : elle contient le titre d'une tâche. Lue au premier
 * accès puis gardée en mémoire ; une valeur illisible ou incohérente (autre version, données abîmées) compte comme absente.
 */
internal class SecureFocusRepository(private val storage: SecureStorage) : FocusRepository {

    private val mutex = Mutex()
    private val cached by lazy { MutableStateFlow(read()) }

    // Première lecture au moment de la collecte (sur le dispatcher io), pas à la création du Flow.
    override fun observe(): Flow<FocusSession?> = flow { emitAll(cached) }

    override suspend fun update(transform: (FocusSession?) -> FocusSession?): FocusSession? = mutex.withLock {
        val current = cached.value
        val next = transform(current)
        if (next != current) {
            if (next == null) {
                storage.remove(KEY)
            } else {
                storage.put(KEY, json.encodeToString(StoredFocus.serializer(), StoredFocus.of(next)))
            }
            cached.value = next
        }
        next
    }

    private fun read(): FocusSession? = storage.get(KEY)?.let { value ->
        runCatching { json.decodeFromString(StoredFocus.serializer(), value).toDomain() }.getOrNull()
    }

    private companion object {
        // Clé stockée sur l'appareil : ne pas la renommer sans migration.
        const val KEY = "focus.session"
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class StoredFocus(
    @SerialName("todo") val todoId: String,
    @SerialName("title") val title: String,
    // Nom Kotlin de la phase : une phase inconnue (autre version) rend la session illisible.
    @SerialName("phase") val phase: String,
    @SerialName("dur") val phaseDurationMillis: Long,
    @SerialName("end") val endsAt: Long? = null,
    @SerialName("left") val pausedRemainingMillis: Long? = null,
    @SerialName("done") val completedWorkPhases: Int,
) {
    /** `null` si la valeur ne décrit pas une session possible : exactement un de `end` / `left`, durées positives. */
    fun toDomain(): FocusSession? {
        val phase = FocusPhase.entries.firstOrNull { it.name == phase } ?: return null
        if (todoId.isBlank() || title.isBlank() || phaseDurationMillis <= 0 || completedWorkPhases < 0) return null
        if ((endsAt == null) == (pausedRemainingMillis == null)) return null
        if (pausedRemainingMillis != null && pausedRemainingMillis !in 0..phaseDurationMillis) return null
        return FocusSession(todoId, title, phase, phaseDurationMillis, endsAt, pausedRemainingMillis, completedWorkPhases)
    }

    companion object {
        fun of(session: FocusSession) = StoredFocus(
            todoId = session.todoId,
            title = session.title,
            phase = session.phase.name,
            phaseDurationMillis = session.phaseDurationMillis,
            endsAt = session.endsAt,
            pausedRemainingMillis = session.pausedRemainingMillis,
            completedWorkPhases = session.completedWorkPhases,
        )
    }
}
