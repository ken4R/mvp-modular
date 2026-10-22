package com.mvp.feature.todocompose.ui

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
import com.mvp.feature.todo.domain.usecase.ObserveRemindersEnabledUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodosUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

data class TodoComposeUiState(
    val todos: List<Todo> = emptyList(),
    val input: String = "",
    val error: String? = null,
    val remindersEnabled: Boolean = true,
) {
    /** Des rappels à venir sont posés mais ne partiront pas : l'écran invite à activer les notifications. */
    fun showRemindersDisabledHint(nowMillis: Long): Boolean =
        !remindersEnabled && todos.any { todo -> !todo.isDone && todo.reminderAt.let { it != null && it > nowMillis } }
}

/**
 * ViewModel partagé Android / iOS (androidx.lifecycle multiplatform) : un seul code d'état pour les deux apps,
 * là où l'écran natif a un TodoViewModel Kotlin (Android) et un TodoViewModel Swift (iOS).
 */
class TodoComposeViewModel(
    observeTodos: ObserveTodosUseCase,
    observeRemindersEnabled: ObserveRemindersEnabledUseCase,
    private val addTodo: AddTodoUseCase,
    private val toggleTodo: ToggleTodoUseCase,
    private val deleteTodo: DeleteTodoUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TodoComposeUiState())
    val state: StateFlow<TodoComposeUiState> = _state.asStateFlow()

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

/** Les use cases viennent du graphe Koin déjà démarré par chaque app (initKoin dans shared). */
internal fun createTodoComposeViewModel(): TodoComposeViewModel = with(KoinPlatform.getKoin()) {
    TodoComposeViewModel(get(), get(), get(), get(), get())
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
