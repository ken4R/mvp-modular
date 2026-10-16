package com.mvp.feature.todo.domain.repository

import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.todo.domain.model.Todo
import kotlinx.coroutines.flow.Flow

interface TodoRepository {
    fun observeTodos(): Flow<List<Todo>>
    suspend fun addTodo(title: String): AppResult<Todo>
    suspend fun toggleTodo(id: String): AppResult<Todo>
    suspend fun deleteTodo(id: String): AppResult<Unit>
}
