package com.mvp.feature.todo.data.source

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.mvp.core.deeplink.domain.DeepLinks
import com.mvp.core.files.PrivateFileStore
import com.mvp.feature.todo.data.db.TodoAttachmentEntity
import com.mvp.feature.todo.data.db.TodoDatabase
import com.mvp.feature.todo.data.db.TodoEntity
import com.mvp.feature.todo.data.stats.TodoStatsLocalDataSource
import com.mvp.feature.todo.data.sync.TodoRecord
import com.mvp.feature.todo.data.sync.TodoSyncLocalDataSource
import com.mvp.feature.todo.domain.calendar.CalendarDate
import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.LocationReminder
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoAttachment
import com.mvp.feature.todo.domain.model.TodoPriority
import com.mvp.feature.todo.domain.model.TrashedTodo
import com.mvp.feature.todo.domain.order.OrderKey
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.UpdateTodoNoteUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Tâches stockées dans SQLite : elles survivent au redémarrage de l'app. Chaque modification du titre, de
 * l'état, de la note, de l'échéance (ADR 0022), de la priorité, de la clé d'ordre (ADR 0024) ou de la mise à la
 * corbeille (ADR 0025) entre dans la file d'envoi de la synchronisation dans la même transaction (ADR 0012) ;
 * les rappels (à l'heure et par lieu, ADR 0023) et les pièces jointes, propres à l'appareil, n'y entrent pas. Le contenu des pièces jointes est dans
 * [files], un fichier par pièce jointe nommé par son id (ADR 0014) : écrit avant sa ligne, supprimé après elle,
 * donc une ligne a toujours son fichier. L'heure à laquelle une tâche est terminée est gardée sur l'appareil pour
 * les statistiques (ADR 0019). [now] date les modifications (epoch ms).
 */
internal class SqlDelightTodoLocalDataSource(
    database: TodoDatabase,
    private val dispatcher: CoroutineDispatcher,
    private val files: PrivateFileStore,
    private val now: () -> Long = ::systemNow,
) : TodoLocalDataSource, TodoSyncLocalDataSource, TodoStatsLocalDataSource {

    private val queries = database.todoEntityQueries
    private val pending = database.todoPendingChangeQueries
    private val attachments = database.todoAttachmentEntityQueries

    override fun observe(): Flow<List<Todo>> =
        queries.selectAll(::rowToTodo).asFlow().mapToList(dispatcher).distinctUntilChanged()

    override fun get(id: String): Todo? = queries.selectWithAttachmentCountById(id, ::rowToTodo).executeAsOneOrNull()

    override fun upsert(todo: Todo) = queries.transaction {
        val existing = queries.selectById(todo.id).executeAsOneOrNull()
        if (existing == null) {
            val changedAt = now()
            val completedAt = changedAt.takeIf { todo.isDone }
            val place = todo.locationReminder
            queries.insert(
                todo.id, todo.title, todo.isDone.toLong(), todo.reminderAt, changedAt, todo.note, completedAt,
                todo.dueDate?.toLong(), place?.latitude, place?.longitude, place?.radiusMeters?.toLong(), place?.label,
                todo.priority.level.toLong(), todo.position, null,
            )
            enqueue(todo.id, changedAt)
        } else {
            write(existing, todo)
        }
    }

    // Lecture et écriture dans une même transaction, sans insert : une tâche supprimée entre-temps
    // n'est pas recréée.
    override fun toggle(id: String): Todo? = modify(id) { it.copy(isDone = !it.isDone) }

    override fun setReminder(id: String, at: Long?): Todo? = modify(id) { it.copy(reminderAt = at) }

    override fun setNote(id: String, note: String): Todo? = modify(id) { it.copy(note = note) }

    override fun setDueDate(id: String, dueDate: Int?): Todo? = modify(id) { it.copy(dueDate = dueDate) }

    override fun setLocationReminder(id: String, reminder: LocationReminder?): Todo? =
        modify(id) { it.copy(locationReminder = reminder) }

    override fun setPriority(id: String, priority: TodoPriority): Todo? = modify(id) { it.copy(priority = priority) }

    override fun setPositions(positions: Map<String, String>) = queries.transaction {
        positions.forEach { (id, position) -> modify(id) { it.copy(position = position) } }
    }

    override fun lastPosition(): String? = queries.selectLastPosition().executeAsOne().ifEmpty { null }

    override fun delete(id: String): Boolean =
        deletePermanently { listOfNotNull(queries.selectById(id).executeAsOneOrNull()) } > 0

    override fun trash(id: String): Boolean = queries.transactionWithResult {
        val existing = queries.selectById(id).executeAsOneOrNull()
        if (existing == null || existing.trashed_at != null) return@transactionWithResult false
        // La modification est datée comme les autres ; l'heure de mise à la corbeille, elle, n'est jamais dans le futur
        // (une tâche reçue avec une heure en avance ne doit pas échapper à la purge des 30 jours).
        val changedAt = changedAfter(existing)
        writeRow(existing, toTodo(existing, 0), updatedAt = changedAt, trashedAt = minOf(changedAt, now()))
        enqueue(id, changedAt)
        true
    }

    override fun restore(id: String): Todo? = queries.transactionWithResult {
        val existing = queries.selectById(id).executeAsOneOrNull()
        if (existing?.trashed_at == null) return@transactionWithResult null
        val changedAt = changedAfter(existing)
        val todo = toTodo(existing, attachments.countByTodo(id).executeAsOne())
        writeRow(existing, todo, updatedAt = changedAt, trashedAt = null)
        enqueue(id, changedAt)
        todo
    }

    override fun observeTrash(): Flow<List<TrashedTodo>> =
        queries.selectTrash(::rowToTrashed).asFlow().mapToList(dispatcher).distinctUntilChanged()

    override fun deleteTrashed(id: String): Boolean =
        deletePermanently { listOfNotNull(queries.selectById(id).executeAsOneOrNull()?.takeIf { it.trashed_at != null }) } > 0

    override fun deleteTrashedBefore(trashedBefore: Long): Int = deletePermanently {
        queries.selectTrashedIdsBefore(trashedBefore).executeAsList().mapNotNull { queries.selectById(it).executeAsOneOrNull() }
    }

    /**
     * Supprime définitivement, dans une transaction, les tâches que [select] y lit : lignes, pièces jointes, et une
     * suppression dans la file d'envoi pour chacune. Les fichiers des pièces jointes sont effacés après, au mieux. Renvoie le
     * nombre de tâches supprimées.
     */
    private fun deletePermanently(select: () -> List<TodoEntity>): Int {
        val (count, removedFiles) = queries.transactionWithResult<Pair<Int, List<String>>> {
            val selected = select()
            val attachmentIds = selected.flatMap { existing ->
                val ids = removeAttachmentsOf(existing.id)
                queries.deleteById(existing.id)
                enqueue(existing.id, changedAfter(existing), isDeleted = true)
                ids
            }
            selected.size to attachmentIds
        }
        // Les tâches sont déjà supprimées : un fichier qui ne s'efface pas reste orphelin (effacé à la déconnexion), sans
        // faire échouer la suppression ni empêcher celle des autres fichiers.
        removedFiles.forEach { runCatching { files.delete(it) } }
        return count
    }

    override fun observeAttachments(todoId: String): Flow<List<TodoAttachment>> =
        attachments.selectByTodo(todoId).asFlow().mapToList(dispatcher)
            .map { rows -> rows.mapNotNull(::toAttachment) }
            .distinctUntilChanged()

    override fun addAttachment(
        id: String,
        todoId: String,
        fileName: String,
        type: AttachmentType,
        bytes: ByteArray,
    ): TodoAttachment? {
        if (queries.selectById(todoId).executeAsOneOrNull()?.takeIf { it.trashed_at == null } == null) return null
        // Le fichier d'abord : une ligne n'existe jamais sans son fichier.
        files.write(id, bytes)
        val addedAt = now()
        val added = try {
            queries.transactionWithResult<Boolean> {
                // La tâche a pu être supprimée (ou mise à la corbeille) pendant l'écriture du fichier.
                if (queries.selectById(todoId).executeAsOneOrNull()?.takeIf { it.trashed_at == null } == null) {
                    return@transactionWithResult false
                }
                attachments.insert(id, todoId, fileName, type.mimeType, bytes.size.toLong(), addedAt)
                true
            }
        } catch (e: Throwable) {
            files.delete(id)
            throw e
        }
        if (!added) {
            files.delete(id)
            return null
        }
        return TodoAttachment(id, todoId, fileName, type, bytes.size.toLong(), addedAt, files.path(id))
    }

    override fun deleteAttachment(id: String): Boolean {
        val existed = attachments.transactionWithResult<Boolean> {
            val existing = attachments.selectById(id).executeAsOneOrNull()
            if (existing != null) attachments.deleteById(id)
            existing != null
        }
        if (existed) files.delete(id)
        return existed
    }

    /**
     * Déconnexion : les tâches, la file d'envoi et les pièces jointes, fichiers compris. Si les fichiers ne
     * s'effacent pas, l'exception fait échouer la déconnexion, qui pourra être retentée (la base est déjà vide).
     */
    override fun clear() {
        queries.transaction {
            queries.deleteAll()
            pending.deleteAll()
            attachments.deleteAll()
        }
        files.deleteAll()
    }

    override fun observeCompletionTimes(): Flow<List<Long>> =
        queries.selectCompletionTimes().asFlow().mapToList(dispatcher).distinctUntilChanged()

    override fun observePendingCount(): Flow<Int> =
        pending.count().asFlow().mapToOne(dispatcher).map { it.toInt() }

    override fun pendingChanges(): List<TodoRecord> =
        pending.selectPending { todoId, isDeleted, changedAt, title, isDone, note, dueDate, priority, position, trashedAt ->
            // Sans ligne dans TodoEntity, la tâche n'existe plus : c'est une suppression.
            if (isDeleted != 0L || title == null || isDone == null) {
                TodoRecord(id = todoId, title = "", isDone = false, updatedAt = changedAt, isDeleted = true)
            } else {
                TodoRecord(
                    id = todoId,
                    title = title,
                    isDone = isDone != 0L,
                    updatedAt = changedAt,
                    note = note.orEmpty(),
                    dueDate = dueDate?.toInt(),
                    priority = priority?.let { TodoPriority.fromLevel(it.toInt()) } ?: TodoPriority.None,
                    position = position.orEmpty(),
                    trashedAt = trashedAt,
                )
            }
        }.executeAsList()

    override fun markSent(sent: List<TodoRecord>) = pending.transaction {
        sent.forEach { pending.deleteSent(it.id, it.updatedAt) }
    }

    override fun applyRemote(records: List<TodoRecord>) {
        val removedFiles = queries.transactionWithResult<List<String>> { records.flatMap(::applyRemoteRecord) }
        removedFiles.forEach(files::delete)
    }

    /** Renvoie les fichiers des pièces jointes retirées (tâche supprimée ailleurs), à supprimer après la transaction. */
    private fun applyRemoteRecord(record: TodoRecord): List<String> {
        if (pending.selectIsPending(record.id).executeAsOne() > 0L) return emptyList()
        // Le serveur n'est pas cru sur parole : une version que l'app n'aurait pas pu écrire est ignorée.
        // Un id qui n'est pas un UUID de l'app finirait dans un lien (partage, calendrier) : ignoré, suppression comprise.
        if (!DeepLinks.isValidTodoId(record.id)) return emptyList()
        // Clé d'ordre : vide (inconnue, la tâche garde la sienne) ou une clé que l'app aurait pu écrire.
        // Heure de mise à la corbeille : jamais après la modification qui l'a faite (ADR 0025).
        val valid = record.title.isValidTitle() && record.note.isValidNote() && record.dueDate.isValidDueDate() &&
            (record.position.isEmpty() || OrderKey.isValid(record.position)) &&
            record.trashedAt.let { it == null || it in 0..record.updatedAt }
        if (!record.isDeleted && !valid) return emptyList()
        val local = queries.selectById(record.id).executeAsOneOrNull()
        // Heure de mise à la corbeille ramenée à l'horloge de l'appareil : une heure dans le futur retarderait la purge
        // indéfiniment. Gardée sur l'appareil seulement, rien ne repart au serveur.
        val trashedAt = record.trashedAt?.let { minOf(it, now()) }
        when {
            // Déjà supprimée ici, ou modifiée ici plus récemment (déjà envoyée) : rien à faire. À égalité
            // d'heure, la version du serveur l'emporte, comme sur le serveur : tous les appareils convergent.
            local == null && record.isDeleted -> Unit
            local != null && local.updated_at > record.updatedAt -> Unit
            record.isDeleted -> {
                val attachmentIds = removeAttachmentsOf(record.id)
                queries.deleteById(record.id)
                return attachmentIds
            }
            // Terminée ailleurs : à l'heure de la modification reçue, l'approximation la plus proche (ADR 0019).
            // Les rappels (à l'heure, par lieu) restent ceux de l'appareil : le serveur n'en connaît aucun.
            local == null -> queries.insert(
                record.id, record.title, record.isDone.toLong(), null, record.updatedAt, record.note,
                record.updatedAt.takeIf { record.isDone }, record.dueDate?.toLong(), null, null, null, null,
                record.priority.level.toLong(), record.position, trashedAt,
            )
            else -> queries.update(
                record.title, record.isDone.toLong(), local.reminder_at, record.updatedAt, record.note,
                completedAt(local, record.isDone, record.updatedAt), record.dueDate?.toLong(),
                local.location_latitude, local.location_longitude, local.location_radius, local.location_label,
                record.priority.level.toLong(), record.position.ifEmpty { local.position }, trashedAt, record.id,
            )
        }
        return emptyList()
    }

    /** Une tâche de la corbeille ne se modifie pas : `null`, comme une tâche qui n'existe pas. */
    private fun modify(id: String, transform: (Todo) -> Todo): Todo? = queries.transactionWithResult {
        val existing = queries.selectById(id).executeAsOneOrNull()?.takeIf { it.trashed_at == null }
            ?: return@transactionWithResult null
        val attachmentCount = attachments.countByTodo(id).executeAsOne()
        transform(toTodo(existing, attachmentCount)).also { write(existing, it) }
    }

    /**
     * Met à jour la ligne ; seul un changement du titre, de l'état, de la note, de l'échéance, de la priorité ou de
     * la clé d'ordre est daté et part au serveur.
     */
    private fun write(existing: TodoEntity, todo: Todo) {
        val synced = existing.title != todo.title || (existing.is_done != 0L) != todo.isDone ||
            existing.note != todo.note || existing.due_date != todo.dueDate?.toLong() ||
            existing.priority != todo.priority.level.toLong() || existing.position != todo.position
        val updatedAt = if (synced) changedAfter(existing) else existing.updated_at
        writeRow(existing, todo, updatedAt, existing.trashed_at)
        if (synced) enqueue(todo.id, updatedAt)
    }

    private fun writeRow(existing: TodoEntity, todo: Todo, updatedAt: Long, trashedAt: Long?) {
        val place = todo.locationReminder
        queries.update(
            todo.title, todo.isDone.toLong(), todo.reminderAt, updatedAt, todo.note,
            completedAt(existing, todo.isDone, updatedAt), todo.dueDate?.toLong(),
            place?.latitude, place?.longitude, place?.radiusMeters?.toLong(), place?.label,
            todo.priority.level.toLong(), todo.position, trashedAt, todo.id,
        )
    }

    /**
     * Heure de fin de la tâche après une modification datée [changedAt] : `null` si elle n'est plus terminée, celle
     * déjà gardée si elle l'était déjà, sinon [changedAt] (elle vient d'être terminée).
     */
    private fun completedAt(existing: TodoEntity, isDone: Boolean, changedAt: Long): Long? = when {
        !isDone -> null
        existing.is_done != 0L -> existing.completed_at
        else -> changedAt
    }

    /** Retire les lignes des pièces jointes de la tâche [todoId] ; renvoie leurs ids, dont les fichiers restent à supprimer. */
    private fun removeAttachmentsOf(todoId: String): List<String> {
        val ids = attachments.selectIdsByTodo(todoId).executeAsList()
        if (ids.isNotEmpty()) attachments.deleteByTodo(todoId)
        return ids
    }

    private fun toAttachment(entity: TodoAttachmentEntity): TodoAttachment? {
        val type = AttachmentType.fromMimeType(entity.mime_type) ?: return null
        return TodoAttachment(
            id = entity.id,
            todoId = entity.todo_id,
            fileName = entity.file_name,
            type = type,
            sizeBytes = entity.size_bytes,
            addedAt = entity.added_at,
            localPath = files.path(entity.id),
        )
    }

    /**
     * Heure d'une nouvelle modification : toujours après la précédente, même si deux modifications tombent dans
     * la même milliseconde ou si l'horloge de l'appareil recule. Sinon [markSent] pourrait retirer de la file
     * une modification faite pendant l'envoi, et le serveur la départagerait mal.
     */
    private fun changedAfter(existing: TodoEntity): Long = maxOf(now(), existing.updated_at + 1)

    private fun enqueue(id: String, changedAt: Long, isDeleted: Boolean = false) =
        pending.upsert(id, if (isDeleted) 1L else 0L, changedAt)
}

private fun toTodo(entity: TodoEntity, attachmentCount: Long) = rowToTodo(
    entity.id, entity.title, entity.is_done, entity.reminder_at, entity.updated_at, entity.note, entity.completed_at,
    entity.due_date, entity.location_latitude, entity.location_longitude, entity.location_radius, entity.location_label,
    entity.priority, entity.position, entity.trashed_at, attachmentCount,
)

/**
 * Une ligne de la corbeille (`selectTrash`). La requête ne lit que des tâches dont `trashed_at` n'est pas NULL.
 */
private fun rowToTrashed(
    id: String,
    title: String,
    isDone: Long,
    reminderAt: Long?,
    updatedAt: Long,
    note: String,
    completedAt: Long?,
    dueDate: Long?,
    locationLatitude: Double?,
    locationLongitude: Double?,
    locationRadius: Long?,
    locationLabel: String?,
    priority: Long,
    position: String,
    trashedAt: Long?,
    attachmentCount: Long,
) = TrashedTodo(
    rowToTodo(
        id, title, isDone, reminderAt, updatedAt, note, completedAt, dueDate, locationLatitude, locationLongitude,
        locationRadius, locationLabel, priority, position, trashedAt, attachmentCount,
    ),
    trashedAt ?: 0L,
)

/** Une ligne de `selectAll` ou `selectWithAttachmentCountById` (colonnes de TodoEntity + nombre de pièces jointes). */
@Suppress("UNUSED_PARAMETER")
private fun rowToTodo(
    id: String,
    title: String,
    isDone: Long,
    reminderAt: Long?,
    updatedAt: Long,
    note: String,
    completedAt: Long?,
    dueDate: Long?,
    locationLatitude: Double?,
    locationLongitude: Double?,
    locationRadius: Long?,
    locationLabel: String?,
    priority: Long,
    position: String,
    trashedAt: Long?,
    attachmentCount: Long,
) = Todo(
    id = id,
    title = title,
    isDone = isDone != 0L,
    reminderAt = reminderAt,
    note = note,
    attachmentCount = attachmentCount.toInt(),
    dueDate = dueDate?.toInt(),
    locationReminder = locationReminder(locationLatitude, locationLongitude, locationRadius, locationLabel),
    // Un niveau inconnu (écrit à la main) vaut « aucune ».
    priority = TodoPriority.fromLevel(priority.toInt()) ?: TodoPriority.None,
    position = position,
)

/** Les quatre colonnes ou aucune : une ligne incomplète (écrite à la main) n'a pas de rappel par lieu. */
private fun locationReminder(latitude: Double?, longitude: Double?, radius: Long?, label: String?): LocationReminder? =
    if (latitude == null || longitude == null || radius == null || label == null) {
        null
    } else {
        LocationReminder(latitude, longitude, radius.toInt(), label)
    }

private fun String.isValidTitle(): Boolean = isNotBlank() && length <= AddTodoUseCase.MAX_TITLE_LENGTH

private fun String.isValidNote(): Boolean = length <= UpdateTodoNoteUseCase.MAX_NOTE_LENGTH

private fun Int?.isValidDueDate(): Boolean = this == null || CalendarDate.isValidDueDate(this)

private fun Boolean.toLong(): Long = if (this) 1L else 0L

@OptIn(ExperimentalTime::class)
private fun systemNow(): Long = Clock.System.now().toEpochMilliseconds()
