package com.mvp.android.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.onFailure
import com.mvp.core.common.domain.result.onSuccess
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodosUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodoUiState(
    val todos: List<Todo> = emptyList(),
    val input: String = "",
    val error: String? = null,
)

class TodoViewModel(
    observeTodos: ObserveTodosUseCase,
    private val addTodo: AddTodoUseCase,
    private val toggleTodo: ToggleTodoUseCase,
    private val deleteTodo: DeleteTodoUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TodoUiState())
    val state: StateFlow<TodoUiState> = _state.asStateFlow()

    init {
        observeTodos(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> _state.update { it.copy(todos = result.data) }
                    is AppResult.Failure -> showError(result.error)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onInputChange(value: String) = _state.update { it.copy(input = value, error = null) }

    fun onAdd() = viewModelScope.launch {
        addTodo(_state.value.input)
            .onSuccess { _state.update { it.copy(input = "", error = null) } }
            .onFailure(::showError)
    }

    fun onToggle(id: String) = viewModelScope.launch { toggleTodo(id).onFailure(::showError) }

    fun onDelete(id: String) = viewModelScope.launch { deleteTodo(id).onFailure(::showError) }

    private fun showError(error: AppError) = _state.update { it.copy(error = error.toMessage()) }
}

private fun AppError.toMessage(): String = when (this) {
    is AppError.Validation -> message
    AppError.NotFound -> "Tâche introuvable"
    AppError.Network -> "Problème de connexion"
    AppError.Unauthorized -> "Accès refusé"
    is AppError.Unknown -> message ?: "Erreur inattendue"
}
