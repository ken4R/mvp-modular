package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Liste des tâches, les non terminées en premier. */
class ObserveTodosUseCase(
    private val repository: TodoRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, List<Todo>>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<List<Todo>>> =
        repository.observeTodos().map { todos -> AppResult.Success(todos.sortedBy { it.isDone }) }
}
