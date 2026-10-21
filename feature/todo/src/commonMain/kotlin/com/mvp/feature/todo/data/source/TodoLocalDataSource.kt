package com.mvp.feature.todo.data.source

import com.mvp.feature.todo.domain.model.Todo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Stockage local des tâches. L'app utilise [SqlDelightTodoLocalDataSource] (SQLite, persistant) ;
 * l'implémentation en mémoire sert aux tests des use cases.
 */
interface TodoLocalDataSource {
    fun observe(): Flow<List<Todo>>
    fun get(id: String): Todo?
    fun upsert(todo: Todo)

    /** Inverse l'état de la tâche en une seule opération ; `null` si elle n'existe pas (ou plus). */
    fun toggle(id: String): Todo?

    /** Pose ou retire le rappel en une seule opération ; `null` si la tâche n'existe pas (ou plus). */
    fun setReminder(id: String, at: Long?): Todo?
    fun delete(id: String): Boolean

    /** Supprime toutes les tâches (déconnexion). */
    fun clear()
}

internal class InMemoryTodoLocalDataSource(initial: List<Todo> = emptyList()) : TodoLocalDataSource {

    private val todos = MutableStateFlow(initial.associateBy { it.id })

    override fun observe(): Flow<List<Todo>> = todos.asStateFlow().map { it.values.toList() }

    override fun get(id: String): Todo? = todos.value[id]

    override fun upsert(todo: Todo) = todos.update { it + (todo.id to todo) }

    override fun toggle(id: String): Todo? = modify(id) { it.copy(isDone = !it.isDone) }

    override fun setReminder(id: String, at: Long?): Todo? = modify(id) { it.copy(reminderAt = at) }

    private fun modify(id: String, transform: (Todo) -> Todo): Todo? {
        var modified: Todo? = null
        todos.update { current ->
            modified = current[id]?.let(transform)
            modified?.let { current + (id to it) } ?: current
        }
        return modified
    }

    override fun delete(id: String): Boolean {
        val existed = todos.value.containsKey(id)
        todos.update { it - id }
        return existed
    }

    override fun clear() {
        todos.value = emptyMap()
    }
}
