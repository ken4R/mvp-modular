package com.mvp.feature.todo.domain.repository

import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoAttachment
import kotlinx.coroutines.flow.Flow

interface TodoRepository {
    fun observeTodos(): Flow<List<Todo>>
    suspend fun addTodo(title: String): AppResult<Todo>
    suspend fun toggleTodo(id: String): AppResult<Todo>
    suspend fun deleteTodo(id: String): AppResult<Unit>
    suspend fun setReminder(id: String, at: Long?): AppResult<Todo>
    suspend fun setNote(id: String, note: String): AppResult<Todo>

    /** [dueDate] en jours depuis le 1970-01-01, `null` pour retirer l'échéance (ADR 0022). */
    suspend fun setDueDate(id: String, dueDate: Int?): AppResult<Todo>

    /** Pièces jointes de la tâche [todoId], dans l'ordre d'ajout ; vide si elle n'existe pas. */
    fun observeAttachments(todoId: String): Flow<List<TodoAttachment>>

    /** Échoue avec [com.mvp.core.common.domain.error.AppError.NotFound] si la tâche n'existe pas (ou plus). */
    suspend fun addAttachment(todoId: String, fileName: String, type: AttachmentType, bytes: ByteArray): AppResult<TodoAttachment>
    suspend fun deleteAttachment(id: String): AppResult<Unit>
}
