package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.feature.todo.domain.model.FilteredTodos
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoFilter
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val byPriority = compareBy<Todo> { it.isDone }.thenByDescending { it.priority.level }

/**
 * Tâches qui correspondent au filtre, dans l'ordre de [ObserveTodosUseCase] (non terminées en premier, puis l'ordre
 * manuel) ; avec [TodoFilter.byPriority], la priorité la plus haute d'abord dans chaque groupe (ADR 0024).
 */
class ObserveFilteredTodosUseCase(
    private val repository: TodoRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<TodoFilter, FilteredTodos>(dispatcher) {

    override fun execute(params: TodoFilter): Flow<AppResult<FilteredTodos>> {
        val matches = params.matcher()
        return repository.observeTodos().map { todos ->
            val sorted = todos.sortedBy { it.isDone }
            // sortedWith est stable : à priorité égale, l'ordre manuel.
            val shown = if (params.byPriority) sorted.sortedWith(byPriority) else sorted
            AppResult.Success(FilteredTodos(todos = shown.filter(matches), all = sorted))
        }
    }
}
