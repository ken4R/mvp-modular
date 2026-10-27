package com.mvp.android.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.onFailure
import com.mvp.core.common.domain.result.onSuccess
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.model.TrashItem
import com.mvp.feature.todo.domain.usecase.DeleteTodoPermanentlyUseCase
import com.mvp.feature.todo.domain.usecase.EmptyTrashUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTrashUseCase
import com.mvp.feature.todo.domain.usecase.PurgeExpiredTrashUseCase
import com.mvp.feature.todo.domain.usecase.RestoreTodoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [items] : `null` tant que la première lecture n'est pas arrivée. [message] : résultat de la dernière action. */
data class TodoTrashUiState(
    val items: List<TrashItem>? = null,
    val message: String? = null,
    val error: Boolean = false,
)

/** Corbeille (ADR 0025) : les règles (30 jours, restauration, suppression définitive) sont dans KMP. */
class TodoTrashViewModel(
    observeTrash: ObserveTrashUseCase,
    purgeExpiredTrash: PurgeExpiredTrashUseCase,
    private val restoreTodo: RestoreTodoUseCase,
    private val deleteTodoPermanently: DeleteTodoPermanentlyUseCase,
    private val emptyTrash: EmptyTrashUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TodoTrashUiState())
    val state: StateFlow<TodoTrashUiState> = _state.asStateFlow()

    init {
        // Une app restée ouverte longtemps supprime aussi ce qui a fait son temps.
        viewModelScope.launch { purgeExpiredTrash(NoParams) }
        observeTrash(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> _state.update { it.copy(items = result.data, error = false) }
                    is AppResult.Failure -> _state.update { it.copy(error = true) }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onRestore(item: TrashItem) = viewModelScope.launch {
        restoreTodo(item.todo.id)
            .onSuccess { show("« ${it.title} » est revenue dans la liste") }
            .onFailure { show("Tâche introuvable") }
    }

    fun onDeletePermanently(item: TrashItem) = viewModelScope.launch {
        deleteTodoPermanently(item.todo.id)
            .onSuccess { show("« ${item.todo.title} » est supprimée définitivement") }
            .onFailure { show("Suppression impossible") }
    }

    fun onEmptyTrash() = viewModelScope.launch {
        emptyTrash(NoParams)
            .onSuccess { show("Corbeille vidée") }
            .onFailure { show("Suppression impossible") }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun show(message: String) = _state.update { it.copy(message = message) }
}
