package com.mvp.feature.todo.data.repository

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.todo.data.source.TodoLocalDataSource
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TrashedTodo
import com.mvp.feature.todo.domain.repository.TodoTrashRepository
import kotlinx.coroutines.flow.Flow

/** Corbeille (ADR 0025), dans la même base que les tâches : chaque changement entre dans la file d'envoi. */
internal class TodoTrashRepositoryImpl(private val local: TodoLocalDataSource) : TodoTrashRepository {

    override fun observeTrash(): Flow<List<TrashedTodo>> = local.observeTrash()

    override suspend fun restore(id: String): AppResult<Todo> =
        local.restore(id)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound)

    // safeCall : l'effacement des fichiers des pièces jointes peut échouer.
    override suspend fun deletePermanently(id: String): AppResult<Unit> =
        when (val deleted = safeCall { local.deleteTrashed(id) }) {
            is AppResult.Success -> if (deleted.data) AppResult.Success(Unit) else AppResult.Failure(AppError.NotFound)
            is AppResult.Failure -> deleted
        }

    override suspend fun deleteTrashedBefore(trashedBefore: Long): AppResult<Int> = safeCall {
        local.deleteTrashedBefore(trashedBefore)
    }
}
