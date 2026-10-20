package com.mvp.feature.todo.data.source

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mvp.feature.todo.data.db.TodoDatabase
import com.mvp.feature.todo.domain.model.Todo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Tâches stockées dans SQLite : elles survivent au redémarrage de l'app. */
internal class SqlDelightTodoLocalDataSource(
    database: TodoDatabase,
    private val dispatcher: CoroutineDispatcher,
) : TodoLocalDataSource {

    private val queries = database.todoEntityQueries

    override fun observe(): Flow<List<Todo>> = queries.selectAll(::toTodo).asFlow().mapToList(dispatcher).distinctUntilChanged()

    override fun get(id: String): Todo? = queries.selectById(id, ::toTodo).executeAsOneOrNull()

    override fun upsert(todo: Todo) = queries.transaction {
        val isDone = if (todo.isDone) 1L else 0L
        if (queries.selectById(todo.id).executeAsOneOrNull() == null) {
            queries.insert(todo.id, todo.title, isDone)
        } else {
            queries.update(todo.title, isDone, todo.id)
        }
    }

    // Lecture et écriture dans une même transaction, sans insert : une tâche supprimée entre-temps
    // n'est pas recréée.
    override fun toggle(id: String): Todo? = queries.transactionWithResult {
        val todo = queries.selectById(id, ::toTodo).executeAsOneOrNull() ?: return@transactionWithResult null
        todo.copy(isDone = !todo.isDone).also { queries.update(it.title, if (it.isDone) 1L else 0L, it.id) }
    }

    override fun delete(id: String): Boolean = queries.transactionWithResult {
        val existed = queries.selectById(id).executeAsOneOrNull() != null
        if (existed) queries.deleteById(id)
        existed
    }

    override fun clear() {
        queries.deleteAll()
    }
}

private fun toTodo(id: String, title: String, isDone: Long) = Todo(id = id, title = title, isDone = isDone != 0L)
