package com.mvp.android.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.model.CompletionStats
import com.mvp.feature.todo.domain.usecase.ObserveCompletionStatsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/** [stats] : `null` tant que le premier calcul n'est pas arrivé. */
data class TodoStatsUiState(val stats: CompletionStats? = null, val error: Boolean = false)

/** Statistiques des tâches terminées (ADR 0019) : le calcul est dans KMP, ici seulement l'état de l'écran. */
class TodoStatsViewModel(observeCompletionStats: ObserveCompletionStatsUseCase) : ViewModel() {

    private val _state = MutableStateFlow(TodoStatsUiState())
    val state: StateFlow<TodoStatsUiState> = _state.asStateFlow()

    init {
        observeCompletionStats(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> _state.update { TodoStatsUiState(stats = result.data) }
                    is AppResult.Failure -> _state.update { it.copy(error = true) }
                }
            }
            .launchIn(viewModelScope)
    }
}
