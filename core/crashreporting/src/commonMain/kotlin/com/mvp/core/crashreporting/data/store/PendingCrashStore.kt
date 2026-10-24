package com.mvp.core.crashreporting.data.store

import com.mvp.core.crashreporting.domain.model.CrashReport
import com.mvp.core.crashreporting.domain.model.CrashedException
import com.mvp.core.files.PrivateFileStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Rapports écrits au moment du plantage, en attendant le lancement suivant : un fichier par rapport dans un
 * dossier privé hors sauvegardes (core:files). Bornés à [capacity] : un plantage en boucle ne remplit pas le disque.
 * Les opérations sont bloquantes ; [save] est appelé par le gestionnaire de plantage, sur le fil qui plante.
 */
internal class PendingCrashStore(private val files: PrivateFileStore, private val capacity: Int = DEFAULT_CAPACITY) {

    private val json = Json { ignoreUnknownKeys = true }

    fun save(report: CrashReport) {
        if (files.names().size >= capacity) return
        files.write(report.id, json.encodeToString(PendingCrash.serializer(), report.toPending()).encodeToByteArray())
    }

    /** Rapports en attente, du plus ancien au plus récent. Un fichier illisible (version précédente, coupé) est effacé. */
    fun readAll(): List<CrashReport> =
        files.names().mapNotNull { name ->
            val bytes = files.read(name) ?: return@mapNotNull null
            runCatching { json.decodeFromString(PendingCrash.serializer(), bytes.decodeToString()).toReport(name) }
                .onFailure { files.delete(name) }
                .getOrNull()
        }.sortedBy { it.occurredAt }

    fun delete(id: String) = files.delete(id)

    fun deleteAll() = files.deleteAll()

    companion object {
        const val DEFAULT_CAPACITY = 10
    }
}

@Serializable
private data class PendingCrash(
    val occurredAt: Long,
    val platform: String,
    val exceptions: List<PendingException>,
)

@Serializable
private data class PendingException(val type: String, val frames: List<String>)

private fun CrashReport.toPending() = PendingCrash(
    occurredAt = occurredAt,
    platform = platform,
    exceptions = exceptions.map { PendingException(it.type, it.frames) },
)

// L'identifiant est le nom du fichier : effacer le rapport envoyé efface bien ce fichier.
private fun PendingCrash.toReport(id: String) = CrashReport(
    id = id,
    occurredAt = occurredAt,
    platform = platform,
    exceptions = exceptions.map { CrashedException(it.type, it.frames) },
)
