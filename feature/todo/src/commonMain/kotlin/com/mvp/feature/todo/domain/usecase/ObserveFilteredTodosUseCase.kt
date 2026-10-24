package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.feature.todo.domain.model.FilteredTodos
import com.mvp.feature.todo.domain.model.TodoFilter
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Tâches qui correspondent au filtre, dans l'ordre de [ObserveTodosUseCase] (non terminées en premier). */
class ObserveFilteredTodosUseCase(
    private val repository: TodoRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<TodoFilter, FilteredTodos>(dispatcher) {

    override fun execute(params: TodoFilter): Flow<AppResult<FilteredTodos>> {
        val matches = params.matcher()
        return repository.observeTodos().map { todos ->
            val sorted = todos.sortedBy { it.isDone }
            AppResult.Success(FilteredTodos(todos = sorted.filter(matches), all = sorted))
        }
    }
}
