package com.mvp.feature.todo.data.source

import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.LocationReminder
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoAttachment
import com.mvp.feature.todo.domain.model.TodoPriority
import com.mvp.feature.todo.domain.model.TrashedTodo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Stockage local des tâches et de leurs pièces jointes. L'app utilise [SqlDelightTodoLocalDataSource]
 * (SQLite et fichiers privés, persistant) ; l'implémentation en mémoire sert aux tests des use cases.
 * Une tâche de la corbeille (ADR 0025) n'est lue que par [observeTrash] : les autres lectures et les modifications
 * l'ignorent, comme une tâche qui n'existe pas, jusqu'à [restore].
 */
interface TodoLocalDataSource {
    /** Hors corbeille, dans l'ordre des clés [Todo.position] ; à clé égale, dans l'ordre d'ajout (ADR 0024). */
    fun observe(): Flow<List<Todo>>
    fun get(id: String): Todo?
    fun upsert(todo: Todo)

    /** Inverse l'état de la tâche en une seule opération ; `null` si elle n'existe pas (ou plus). */
    fun toggle(id: String): Todo?

    /** Pose ou retire le rappel en une seule opération ; `null` si la tâche n'existe pas (ou plus). */
    fun setReminder(id: String, at: Long?): Todo?

    /** Remplace la note en une seule opération ; `null` si la tâche n'existe pas (ou plus). */
    fun setNote(id: String, note: String): Todo?

    /** Pose ou retire l'échéance en une seule opération ; `null` si la tâche n'existe pas (ou plus). */
    fun setDueDate(id: String, dueDate: Int?): Todo?

    /** Pose ou retire le rappel par lieu en une seule opération ; `null` si la tâche n'existe pas (ou plus). */
    fun setLocationReminder(id: String, reminder: LocationReminder?): Todo?

    /** Change la priorité en une seule opération ; `null` si la tâche n'existe pas (ou plus). */
    fun setPriority(id: String, priority: TodoPriority): Todo?

    /** Écrit les clés d'ordre (id → clé) en une seule opération ; les tâches qui n'existent plus sont ignorées. */
    fun setPositions(positions: Map<String, String>)

    /** Plus grande clé d'ordre, `null` sans tâche. */
    fun lastPosition(): String?

    /** Supprime définitivement la tâche (dans la corbeille ou non) et ses pièces jointes. */
    fun delete(id: String): Boolean

    /** Met la tâche à la corbeille, pièces jointes gardées ; `false` si elle n'existe pas ou y est déjà. */
    fun trash(id: String): Boolean

    /** Sort la tâche de la corbeille ; `null` si elle n'y est pas. */
    fun restore(id: String): Todo?

    /** Tâches de la corbeille, la dernière jetée en premier. */
    fun observeTrash(): Flow<List<TrashedTodo>>

    /** Supprime définitivement la tâche [id] si elle est dans la corbeille. */
    fun deleteTrashed(id: String): Boolean

    /** Supprime définitivement les tâches jetées au plus tard à [trashedBefore] (epoch ms) ; renvoie leur nombre. */
    fun deleteTrashedBefore(trashedBefore: Long): Int

    /** Pièces jointes de la tâche [todoId], dans l'ordre d'ajout. */
    fun observeAttachments(todoId: String): Flow<List<TodoAttachment>>

    /** Enregistre le fichier puis la pièce jointe [id] ; `null` (rien n'est gardé) si la tâche n'existe pas (ou plus). */
    fun addAttachment(id: String, todoId: String, fileName: String, type: AttachmentType, bytes: ByteArray): TodoAttachment?
    fun deleteAttachment(id: String): Boolean

    /** Supprime toutes les tâches et leurs pièces jointes (déconnexion). */
    fun clear()
}

/** [now] date les mises à la corbeille (epoch ms). */
internal class InMemoryTodoLocalDataSource(
    initial: List<Todo> = emptyList(),
    private val now: () -> Long = { 0L },
) : TodoLocalDataSource {

    private val todos = MutableStateFlow(initial.associateBy { it.id })
    private val attachments = MutableStateFlow<List<TodoAttachment>>(emptyList())

    /** Corbeille : id → heure de la mise à la corbeille. */
    private val trashed = MutableStateFlow<Map<String, Long>>(emptyMap())

    override fun observe(): Flow<List<Todo>> =
        combine(todos, attachments, trashed) { todos, attachments, trashed ->
            // sortedBy est stable : à clé égale, l'ordre d'ajout, comme le rowid de SQLite.
            todos.values.filterNot { it.id in trashed }.sortedBy { it.position }.map { it.withCount(attachments) }
        }

    override fun get(id: String): Todo? = todos.value[id]?.takeUnless { id in trashed.value }?.withCount(attachments.value)

    override fun upsert(todo: Todo) = todos.update { it + (todo.id to todo) }

    override fun toggle(id: String): Todo? = modify(id) { it.copy(isDone = !it.isDone) }

    override fun setReminder(id: String, at: Long?): Todo? = modify(id) { it.copy(reminderAt = at) }

    override fun setNote(id: String, note: String): Todo? = modify(id) { it.copy(note = note) }

    override fun setDueDate(id: String, dueDate: Int?): Todo? = modify(id) { it.copy(dueDate = dueDate) }

    override fun setLocationReminder(id: String, reminder: LocationReminder?): Todo? =
        modify(id) { it.copy(locationReminder = reminder) }

    override fun setPriority(id: String, priority: TodoPriority): Todo? = modify(id) { it.copy(priority = priority) }

    override fun setPositions(positions: Map<String, String>) = todos.update { current ->
        current.mapValues { (id, todo) -> positions[id]?.takeUnless { id in trashed.value }?.let { todo.copy(position = it) } ?: todo }
    }

    override fun lastPosition(): String? = todos.value.values.maxOfOrNull { it.position }

    private fun modify(id: String, transform: (Todo) -> Todo): Todo? {
        var modified: Todo? = null
        if (id in trashed.value) return null
        todos.update { current ->
            modified = current[id]?.let(transform)
            modified?.let { current + (id to it) } ?: current
        }
        return modified?.withCount(attachments.value)
    }

    override fun delete(id: String): Boolean {
        val existed = todos.value.containsKey(id)
        todos.update { it - id }
        trashed.update { it - id }
        attachments.update { list -> list.filterNot { it.todoId == id } }
        return existed
    }

    override fun trash(id: String): Boolean {
        if (!todos.value.containsKey(id) || id in trashed.value) return false
        trashed.update { it + (id to now()) }
        return true
    }

    override fun restore(id: String): Todo? {
        if (id !in trashed.value) return null
        trashed.update { it - id }
        return get(id)
    }

    override fun observeTrash(): Flow<List<TrashedTodo>> =
        combine(todos, attachments, trashed) { todos, attachments, trashed ->
            trashed.entries.sortedByDescending { it.value }
                .mapNotNull { (id, at) -> todos[id]?.let { TrashedTodo(it.withCount(attachments), at) } }
        }

    override fun deleteTrashed(id: String): Boolean = id in trashed.value && delete(id)

    override fun deleteTrashedBefore(trashedBefore: Long): Int =
        trashed.value.filterValues { it <= trashedBefore }.keys.count(::delete)

    override fun observeAttachments(todoId: String): Flow<List<TodoAttachment>> =
        attachments.map { list -> list.filter { it.todoId == todoId } }

    override fun addAttachment(
        id: String,
        todoId: String,
        fileName: String,
        type: AttachmentType,
        bytes: ByteArray,
    ): TodoAttachment? {
        if (!todos.value.containsKey(todoId) || todoId in trashed.value) return null
        val attachment = TodoAttachment(id, todoId, fileName, type, bytes.size.toLong(), addedAt = 0L, localPath = "memory/$id")
        attachments.update { it + attachment }
        return attachment
    }

    override fun deleteAttachment(id: String): Boolean {
        val existed = attachments.value.any { it.id == id }
        attachments.update { list -> list.filterNot { it.id == id } }
        return existed
    }

    override fun clear() {
        todos.value = emptyMap()
        trashed.value = emptyMap()
        attachments.value = emptyList()
    }

    private fun Todo.withCount(attachments: List<TodoAttachment>) =
        copy(attachmentCount = attachments.count { it.todoId == id })
}
