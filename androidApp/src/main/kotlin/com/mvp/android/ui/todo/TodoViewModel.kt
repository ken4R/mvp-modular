package com.mvp.android.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.onFailure
import com.mvp.core.common.domain.result.onSuccess
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoReminder
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.FindTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveRemindersEnabledUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodosUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoReminderUseCase
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
    val remindersEnabled: Boolean = true,
    /** Tâche ouverte par un lien : la liste défile jusqu'à elle et la met en évidence quelques secondes. */
    val focusedTodoId: String? = null,
) {
    /** Des rappels à venir sont posés mais ne partiront pas : l'écran invite à activer les notifications. */
    val showRemindersDisabledHint: Boolean
        get() {
            val now = System.currentTimeMillis()
            return !remindersEnabled && todos.any { todo -> !todo.isDone && todo.reminderAt.let { it != null && it > now } }
        }
}

class TodoViewModel(
    observeTodos: ObserveTodosUseCase,
    observeRemindersEnabled: ObserveRemindersEnabledUseCase,
    private val addTodo: AddTodoUseCase,
    private val toggleTodo: ToggleTodoUseCase,
    private val deleteTodo: DeleteTodoUseCase,
    private val findTodo: FindTodoUseCase,
    private val setTodoReminder: SetTodoReminderUseCase,
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
        observeRemindersEnabled(NoParams)
            .onEach { result -> if (result is AppResult.Success) _state.update { it.copy(remindersEnabled = result.data) } }
            .launchIn(viewModelScope)
    }

    /** Un lien demande la tâche [id] : mise en évidence si elle existe ; sinon l'écran reste sur la liste et le dit. */
    fun focus(id: String) = viewModelScope.launch {
        findTodo(id)
            .onSuccess { todo -> _state.update { it.copy(focusedTodoId = todo.id) } }
            .onFailure(::showError)
    }

    fun clearFocus() = _state.update { it.copy(focusedTodoId = null) }

    fun onInputChange(value: String) = _state.update { it.copy(input = value, error = null) }

    fun onAdd() = viewModelScope.launch {
        addTodo(_state.value.input)
            .onSuccess { _state.update { it.copy(input = "", error = null) } }
            .onFailure(::showError)
    }

    fun onToggle(id: String) = viewModelScope.launch { toggleTodo(id).onFailure(::showError) }

    fun onDelete(id: String) = viewModelScope.launch {
        if (_state.value.focusedTodoId == id) clearFocus()
        deleteTodo(id).onFailure(::showError)
    }

    /** [at] : heure choisie (epoch ms), `null` pour retirer le rappel. */
    fun onSetReminder(id: String, at: Long?) = viewModelScope.launch {
        setTodoReminder(TodoReminder(id, at))
            .onSuccess { _state.update { it.copy(error = null) } }
            .onFailure(::showError)
    }

    private fun showError(error: AppError) = _state.update { it.copy(error = error.toMessage()) }
}

private fun AppError.toMessage(): String = when (this) {
    is AppError.Validation -> message
    AppError.NotFound -> "Tâche introuvable"
    AppError.Network -> "Problème de connexion"
    AppError.Unauthorized -> "Accès refusé"
    AppError.PermissionDenied -> "Autorisation refusée"
    AppError.Unavailable -> "Service indisponible"
    is AppError.Unknown -> message ?: "Erreur inattendue"
}
