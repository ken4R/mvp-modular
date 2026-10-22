package com.mvp.feature.todo.data.source

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.mvp.feature.todo.data.db.TodoDatabase
import com.mvp.feature.todo.data.db.TodoEntity
import com.mvp.feature.todo.data.sync.TodoRecord
import com.mvp.feature.todo.data.sync.TodoSyncLocalDataSource
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Tâches stockées dans SQLite : elles survivent au redémarrage de l'app. Chaque modification du titre ou de
 * l'état entre dans la file d'envoi de la synchronisation dans la même transaction (ADR 0012) ; le rappel,
 * propre à l'appareil, n'y entre pas. [now] date les modifications (epoch ms).
 */
internal class SqlDelightTodoLocalDataSource(
    database: TodoDatabase,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Long = ::systemNow,
) : TodoLocalDataSource, TodoSyncLocalDataSource {

    private val queries = database.todoEntityQueries
    private val pending = database.todoPendingChangeQueries

    override fun observe(): Flow<List<Todo>> =
        queries.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map(::toTodo) }.distinctUntilChanged()

    override fun get(id: String): Todo? = queries.selectById(id).executeAsOneOrNull()?.let(::toTodo)

    override fun upsert(todo: Todo) = queries.transaction {
        val existing = queries.selectById(todo.id).executeAsOneOrNull()
        if (existing == null) {
            val changedAt = now()
            queries.insert(todo.id, todo.title, todo.isDone.toLong(), todo.reminderAt, changedAt)
            enqueue(todo.id, changedAt)
        } else {
            write(existing, todo)
        }
    }

    // Lecture et écriture dans une même transaction, sans insert : une tâche supprimée entre-temps
    // n'est pas recréée.
    override fun toggle(id: String): Todo? = modify(id) { it.copy(isDone = !it.isDone) }

    override fun setReminder(id: String, at: Long?): Todo? = modify(id) { it.copy(reminderAt = at) }

    override fun delete(id: String): Boolean = queries.transactionWithResult {
        val existing = queries.selectById(id).executeAsOneOrNull()
        if (existing != null) {
            queries.deleteById(id)
            enqueue(id, changedAfter(existing), isDeleted = true)
        }
        existing != null
    }

    /** Déconnexion : les tâches et la file d'envoi. */
    override fun clear() = queries.transaction {
        queries.deleteAll()
        pending.deleteAll()
    }

    override fun observePendingCount(): Flow<Int> =
        pending.count().asFlow().mapToOne(dispatcher).map { it.toInt() }

    override fun pendingChanges(): List<TodoRecord> =
        pending.selectPending { todoId, isDeleted, changedAt, title, isDone ->
            // Sans ligne dans TodoEntity, la tâche n'existe plus : c'est une suppression.
            if (isDeleted != 0L || title == null || isDone == null) {
                TodoRecord(id = todoId, title = "", isDone = false, updatedAt = changedAt, isDeleted = true)
            } else {
                TodoRecord(id = todoId, title = title, isDone = isDone != 0L, updatedAt = changedAt)
            }
        }.executeAsList()

    override fun markSent(sent: List<TodoRecord>) = pending.transaction {
        sent.forEach { pending.deleteSent(it.id, it.updatedAt) }
    }

    override fun applyRemote(records: List<TodoRecord>) = queries.transaction {
        records.forEach { record ->
            if (pending.selectIsPending(record.id).executeAsOne() > 0L) return@forEach
            // Le serveur n'est pas cru sur parole : une version que l'app n'aurait pas pu écrire est ignorée.
            if (!record.isDeleted && !record.title.isValidTitle()) return@forEach
            val local = queries.selectById(record.id).executeAsOneOrNull()
            when {
                // Déjà supprimée ici, ou modifiée ici plus récemment (déjà envoyée) : rien à faire. À égalité
                // d'heure, la version du serveur l'emporte, comme sur le serveur : tous les appareils convergent.
                local == null && record.isDeleted -> Unit
                local != null && local.updated_at > record.updatedAt -> Unit
                record.isDeleted -> queries.deleteById(record.id)
                local == null -> queries.insert(record.id, record.title, record.isDone.toLong(), null, record.updatedAt)
                else -> queries.update(record.title, record.isDone.toLong(), local.reminder_at, record.updatedAt, record.id)
            }
        }
    }

    private fun modify(id: String, transform: (Todo) -> Todo): Todo? = queries.transactionWithResult {
        val existing = queries.selectById(id).executeAsOneOrNull() ?: return@transactionWithResult null
        transform(toTodo(existing)).also { write(existing, it) }
    }

    /** Met à jour la ligne ; seul un changement du titre ou de l'état est daté et part au serveur. */
    private fun write(existing: TodoEntity, todo: Todo) {
        val synced = existing.title != todo.title || (existing.is_done != 0L) != todo.isDone
        val updatedAt = if (synced) changedAfter(existing) else existing.updated_at
        queries.update(todo.title, todo.isDone.toLong(), todo.reminderAt, updatedAt, todo.id)
        if (synced) enqueue(todo.id, updatedAt)
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

private fun toTodo(entity: TodoEntity) =
    Todo(id = entity.id, title = entity.title, isDone = entity.is_done != 0L, reminderAt = entity.reminder_at)

private fun String.isValidTitle(): Boolean = isNotBlank() && length <= AddTodoUseCase.MAX_TITLE_LENGTH

private fun Boolean.toLong(): Long = if (this) 1L else 0L

@OptIn(ExperimentalTime::class)
private fun systemNow(): Long = Clock.System.now().toEpochMilliseconds()
