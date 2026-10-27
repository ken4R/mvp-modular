package com.mvp.feature.todo.data.source

import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.LocationReminder
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoAttachment
import com.mvp.feature.todo.domain.model.TodoPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Stockage local des tâches et de leurs pièces jointes. L'app utilise [SqlDelightTodoLocalDataSource]
 * (SQLite et fichiers privés, persistant) ; l'implémentation en mémoire sert aux tests des use cases.
 */
interface TodoLocalDataSource {
    /** Dans l'ordre des clés [Todo.position] ; à clé égale, dans l'ordre d'ajout (ADR 0024). */
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

    /** Supprime la tâche et ses pièces jointes. */
    fun delete(id: String): Boolean

    /** Pièces jointes de la tâche [todoId], dans l'ordre d'ajout. */
    fun observeAttachments(todoId: String): Flow<List<TodoAttachment>>

    /** Enregistre le fichier puis la pièce jointe [id] ; `null` (rien n'est gardé) si la tâche n'existe pas (ou plus). */
    fun addAttachment(id: String, todoId: String, fileName: String, type: AttachmentType, bytes: ByteArray): TodoAttachment?
    fun deleteAttachment(id: String): Boolean

    /** Supprime toutes les tâches et leurs pièces jointes (déconnexion). */
    fun clear()
}

internal class InMemoryTodoLocalDataSource(initial: List<Todo> = emptyList()) : TodoLocalDataSource {

    private val todos = MutableStateFlow(initial.associateBy { it.id })
    private val attachments = MutableStateFlow<List<TodoAttachment>>(emptyList())

    override fun observe(): Flow<List<Todo>> =
        combine(todos, attachments) { todos, attachments ->
            // sortedBy est stable : à clé égale, l'ordre d'ajout, comme le rowid de SQLite.
            todos.values.sortedBy { it.position }.map { it.withCount(attachments) }
        }

    override fun get(id: String): Todo? = todos.value[id]?.withCount(attachments.value)

    override fun upsert(todo: Todo) = todos.update { it + (todo.id to todo) }

    override fun toggle(id: String): Todo? = modify(id) { it.copy(isDone = !it.isDone) }

    override fun setReminder(id: String, at: Long?): Todo? = modify(id) { it.copy(reminderAt = at) }

    override fun setNote(id: String, note: String): Todo? = modify(id) { it.copy(note = note) }

    override fun setDueDate(id: String, dueDate: Int?): Todo? = modify(id) { it.copy(dueDate = dueDate) }

    override fun setLocationReminder(id: String, reminder: LocationReminder?): Todo? =
        modify(id) { it.copy(locationReminder = reminder) }

    override fun setPriority(id: String, priority: TodoPriority): Todo? = modify(id) { it.copy(priority = priority) }

    override fun setPositions(positions: Map<String, String>) = todos.update { current ->
        current.mapValues { (id, todo) -> positions[id]?.let { todo.copy(position = it) } ?: todo }
    }

    override fun lastPosition(): String? = todos.value.values.maxOfOrNull { it.position }

    private fun modify(id: String, transform: (Todo) -> Todo): Todo? {
        var modified: Todo? = null
        todos.update { current ->
            modified = current[id]?.let(transform)
            modified?.let { current + (id to it) } ?: current
        }
        return modified?.withCount(attachments.value)
    }

    override fun delete(id: String): Boolean {
        val existed = todos.value.containsKey(id)
        todos.update { it - id }
        attachments.update { list -> list.filterNot { it.todoId == id } }
        return existed
    }

    override fun observeAttachments(todoId: String): Flow<List<TodoAttachment>> =
        attachments.map { list -> list.filter { it.todoId == todoId } }

    override fun addAttachment(
        id: String,
        todoId: String,
        fileName: String,
        type: AttachmentType,
        bytes: ByteArray,
    ): TodoAttachment? {
        if (!todos.value.containsKey(todoId)) return null
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
        attachments.value = emptyList()
    }

    private fun Todo.withCount(attachments: List<TodoAttachment>) =
        copy(attachmentCount = attachments.count { it.todoId == id })
}
