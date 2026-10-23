package com.mvp.feature.todo.data.repository

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.todo.data.source.TodoLocalDataSource
import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoAttachment
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** [idGenerator] : ids des tâches et des pièces jointes (ces derniers nomment leurs fichiers). */
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

    override suspend fun setNote(id: String, note: String): AppResult<Todo> =
        local.setNote(id, note)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound)

    override fun observeAttachments(todoId: String): Flow<List<TodoAttachment>> = local.observeAttachments(todoId)

    // safeCall : l'écriture du fichier peut échouer (stockage plein).
    override suspend fun addAttachment(
        todoId: String,
        fileName: String,
        type: AttachmentType,
        bytes: ByteArray,
    ): AppResult<TodoAttachment> =
        when (val added = safeCall { local.addAttachment(idGenerator(), todoId, fileName, type, bytes) }) {
            is AppResult.Success -> added.data?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound)
            is AppResult.Failure -> added
        }

    override suspend fun deleteAttachment(id: String): AppResult<Unit> =
        if (local.deleteAttachment(id)) AppResult.Success(Unit) else AppResult.Failure(AppError.NotFound)
}

@OptIn(ExperimentalUuidApi::class)
private fun randomId(): String = Uuid.random().toString()
