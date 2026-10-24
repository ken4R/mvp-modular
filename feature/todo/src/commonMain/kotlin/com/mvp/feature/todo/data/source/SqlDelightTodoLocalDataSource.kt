package com.mvp.feature.todo.data.source

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.mvp.core.files.PrivateFileStore
import com.mvp.feature.todo.data.db.TodoAttachmentEntity
import com.mvp.feature.todo.data.db.TodoDatabase
import com.mvp.feature.todo.data.db.TodoEntity
import com.mvp.feature.todo.data.stats.TodoStatsLocalDataSource
import com.mvp.feature.todo.data.sync.TodoRecord
import com.mvp.feature.todo.data.sync.TodoSyncLocalDataSource
import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoAttachment
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
 * l'état ou de la note entre dans la file d'envoi de la synchronisation dans la même transaction (ADR 0012) ;
 * le rappel et les pièces jointes, propres à l'appareil, n'y entrent pas. Le contenu des pièces jointes est dans
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
            queries.insert(todo.id, todo.title, todo.isDone.toLong(), todo.reminderAt, changedAt, todo.note, completedAt)
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

    override fun delete(id: String): Boolean {
        val removedFiles = queries.transactionWithResult<List<String>?> {
            val existing = queries.selectById(id).executeAsOneOrNull() ?: return@transactionWithResult null
            val attachmentIds = removeAttachmentsOf(id)
            queries.deleteById(id)
            enqueue(id, changedAfter(existing), isDeleted = true)
            attachmentIds
        } ?: return false
        removedFiles.forEach(files::delete)
        return true
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
        if (queries.selectById(todoId).executeAsOneOrNull() == null) return null
        // Le fichier d'abord : une ligne n'existe jamais sans son fichier.
        files.write(id, bytes)
        val addedAt = now()
        val added = try {
            queries.transactionWithResult<Boolean> {
                // La tâche a pu être supprimée pendant l'écriture du fichier.
                if (queries.selectById(todoId).executeAsOneOrNull() == null) return@transactionWithResult false
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
        pending.selectPending { todoId, isDeleted, changedAt, title, isDone, note ->
            // Sans ligne dans TodoEntity, la tâche n'existe plus : c'est une suppression.
            if (isDeleted != 0L || title == null || isDone == null) {
                TodoRecord(id = todoId, title = "", isDone = false, updatedAt = changedAt, isDeleted = true)
            } else {
                TodoRecord(id = todoId, title = title, isDone = isDone != 0L, updatedAt = changedAt, note = note.orEmpty())
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
        if (!record.isDeleted && !(record.title.isValidTitle() && record.note.isValidNote())) return emptyList()
        val local = queries.selectById(record.id).executeAsOneOrNull()
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
            local == null -> queries.insert(
                record.id, record.title, record.isDone.toLong(), null, record.updatedAt, record.note,
                record.updatedAt.takeIf { record.isDone },
            )
            else -> queries.update(
                record.title, record.isDone.toLong(), local.reminder_at, record.updatedAt, record.note,
                completedAt(local, record.isDone, record.updatedAt), record.id,
            )
        }
        return emptyList()
    }

    private fun modify(id: String, transform: (Todo) -> Todo): Todo? = queries.transactionWithResult {
        val existing = queries.selectById(id).executeAsOneOrNull() ?: return@transactionWithResult null
        val attachmentCount = attachments.countByTodo(id).executeAsOne()
        transform(toTodo(existing, attachmentCount)).also { write(existing, it) }
    }

    /** Met à jour la ligne ; seul un changement du titre, de l'état ou de la note est daté et part au serveur. */
    private fun write(existing: TodoEntity, todo: Todo) {
        val synced = existing.title != todo.title || (existing.is_done != 0L) != todo.isDone || existing.note != todo.note
        val updatedAt = if (synced) changedAfter(existing) else existing.updated_at
        queries.update(
            todo.title, todo.isDone.toLong(), todo.reminderAt, updatedAt, todo.note,
            completedAt(existing, todo.isDone, updatedAt), todo.id,
        )
        if (synced) enqueue(todo.id, updatedAt)
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
    attachmentCount,
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
    attachmentCount: Long,
) = Todo(id = id, title = title, isDone = isDone != 0L, reminderAt = reminderAt, note = note, attachmentCount = attachmentCount.toInt())

private fun String.isValidTitle(): Boolean = isNotBlank() && length <= AddTodoUseCase.MAX_TITLE_LENGTH

private fun String.isValidNote(): Boolean = length <= UpdateTodoNoteUseCase.MAX_NOTE_LENGTH

private fun Boolean.toLong(): Long = if (this) 1L else 0L

@OptIn(ExperimentalTime::class)
private fun systemNow(): Long = Clock.System.now().toEpochMilliseconds()
