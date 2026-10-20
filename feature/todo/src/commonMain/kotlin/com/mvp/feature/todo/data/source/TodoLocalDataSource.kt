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
    fun delete(id: String): Boolean
}

internal class InMemoryTodoLocalDataSource(initial: List<Todo> = emptyList()) : TodoLocalDataSource {

    private val todos = MutableStateFlow(initial.associateBy { it.id })

    override fun observe(): Flow<List<Todo>> = todos.asStateFlow().map { it.values.toList() }

    override fun get(id: String): Todo? = todos.value[id]

    override fun upsert(todo: Todo) = todos.update { it + (todo.id to todo) }

    override fun delete(id: String): Boolean {
        val existed = todos.value.containsKey(id)
        todos.update { it - id }
        return existed
    }
}
