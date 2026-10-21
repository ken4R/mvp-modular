package com.mvp.feature.todo.data.repository

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.todo.data.source.TodoLocalDataSource
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal class TodoRepositoryImpl(
    private val local: TodoLocalDataSource,
    private val idGenerator: () -> String = ::randomId,
) : TodoRepository {

    override fun observeTodos(): Flow<List<Todo>> = local.observe()

    override suspend fun addTodo(title: String): AppResult<Todo> = safeCall {
        Todo(id = idGenerator(), title = title, isDone = false).also(local::upsert)
    }

    override suspend fun toggleTodo(id: String): AppResult<Todo> =
        local.toggle(id)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound)

    override suspend fun deleteTodo(id: String): AppResult<Unit> =
        if (local.delete(id)) AppResult.Success(Unit) else AppResult.Failure(AppError.NotFound)

    override suspend fun setReminder(id: String, at: Long?): AppResult<Todo> =
        local.setReminder(id, at)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound)
}

@OptIn(ExperimentalUuidApi::class)
private fun randomId(): String = Uuid.random().toString()
