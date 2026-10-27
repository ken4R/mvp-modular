package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.model.TodoMove
import com.mvp.feature.todo.domain.order.TodoOrder
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first

/**
 * Glisser-déposer (ADR 0024) : pose une tâche juste après une autre, dans son groupe (à faire ou terminées). Une
 * tâche lâchée sous une tâche de l'autre groupe va au bout du sien : en bas des tâches à faire, en haut des
 * terminées. Seules les clés d'ordre qui changent sont écrites, et partent au serveur comme une modification de la
 * tâche. Fonctionne aussi sur une liste filtrée : la tâche se place après sa voisine visible.
 */
class MoveTodoUseCase(
    private val repository: TodoRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<TodoMove, Unit>(dispatcher) {

    override suspend fun execute(params: TodoMove): AppResult<Unit> {
        if (params.afterTodoId == params.todoId) return AppResult.Success(Unit)
        val todos = repository.observeTodos().first()
        val moved = todos.firstOrNull { it.id == params.todoId } ?: return AppResult.Failure(AppError.NotFound)
        val section = todos.filter { it.isDone == moved.isDone }
        val index = when (val afterId = params.afterTodoId) {
            null -> 0
            else -> {
                val after = todos.firstOrNull { it.id == afterId } ?: return AppResult.Failure(AppError.NotFound)
                when {
                    after.isDone == moved.isDone -> section.filterNot { it.id == moved.id }.indexOfFirst { it.id == afterId } + 1
                    // Lâchée sous une terminée : en bas des tâches à faire. Sous une tâche à faire : en haut des terminées.
                    after.isDone -> section.size
                    else -> 0
                }
            }
        }
        val positions = TodoOrder.planMove(section, moved.id, index)
        return if (positions.isEmpty()) AppResult.Success(Unit) else repository.setPositions(positions)
    }
}
