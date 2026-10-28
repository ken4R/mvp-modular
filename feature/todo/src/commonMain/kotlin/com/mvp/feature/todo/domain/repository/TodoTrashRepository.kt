package com.mvp.feature.todo.domain.repository

import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TrashedTodo
import kotlinx.coroutines.flow.Flow

/**
 * Corbeille des tâches (ADR 0025). On y met une tâche avec [TodoRepository.deleteTodo] ; elle en sort restaurée ou
 * supprimée définitivement. Chaque changement part au serveur par la file d'envoi (ADR 0012).
 */
interface TodoTrashRepository {
    /** La dernière jetée en premier. */
    fun observeTrash(): Flow<List<TrashedTodo>>

    /** Remet la tâche dans la liste, à sa place ; [com.mvp.core.common.domain.error.AppError.NotFound] si elle n'est pas dans la corbeille. */
    suspend fun restore(id: String): AppResult<Todo>

    /** Supprime définitivement une tâche de la corbeille, pièces jointes comprises ; NotFound si elle n'y est pas. */
    suspend fun deletePermanently(id: String): AppResult<Unit>

    /** Supprime définitivement les tâches jetées au plus tard à [trashedBefore] (epoch ms) ; renvoie leur nombre. */
    suspend fun deleteTrashedBefore(trashedBefore: Long): AppResult<Int>
}
