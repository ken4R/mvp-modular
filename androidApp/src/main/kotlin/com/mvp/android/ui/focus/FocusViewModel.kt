package com.mvp.android.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.focus.domain.model.FocusTimer
import com.mvp.feature.focus.domain.usecase.ObserveFocusTimerUseCase
import com.mvp.feature.focus.domain.usecase.PauseFocusUseCase
import com.mvp.feature.focus.domain.usecase.ResumeFocusUseCase
import com.mvp.feature.focus.domain.usecase.StartFocus
import com.mvp.feature.focus.domain.usecase.StartFocusUseCase
import com.mvp.feature.focus.domain.usecase.StartNextFocusPhaseUseCase
import com.mvp.feature.focus.domain.usecase.StopFocusUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [timer] : `null` sans session de concentration. [loaded] : première valeur reçue. */
data class FocusUiState(val timer: FocusTimer? = null, val loaded: Boolean = false, val error: String? = null)

/**
 * Mode focus (ADR 0028) : compte à rebours et règles dans KMP, ici seulement l'état de l'écran et les boutons.
 * La notification en cours est tenue à part (FocusNotifier), pour vivre sans l'écran.
 */
class FocusViewModel(
    observeTimer: ObserveFocusTimerUseCase,
    private val startFocus: StartFocusUseCase,
    private val pauseFocus: PauseFocusUseCase,
    private val resumeFocus: ResumeFocusUseCase,
    private val startNextPhase: StartNextFocusPhaseUseCase,
    private val stopFocus: StopFocusUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(FocusUiState())
    val state: StateFlow<FocusUiState> = _state.asStateFlow()

    init {
        observeTimer(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> _state.update { it.copy(timer = result.data, loaded = true) }
                    is AppResult.Failure -> _state.update { it.copy(loaded = true, error = "Mode focus indisponible") }
                }
            }
            .launchIn(viewModelScope)
    }

    /** [onStarted] : appelé seulement si la session a démarré (l'écran Focus ne s'ouvre pas sur un échec). */
    fun start(todoId: String, title: String, onStarted: () -> Unit) =
        perform(onSuccess = onStarted) { startFocus(StartFocus(todoId, title)) }
    fun pause() = perform { pauseFocus(NoParams) }
    fun resume() = perform { resumeFocus(NoParams) }
    fun next() = perform { startNextPhase(NoParams) }
    fun stop() = perform { stopFocus(NoParams) }

    private fun perform(onSuccess: () -> Unit = {}, action: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            val result = action()
            if (result is AppResult.Success) onSuccess()
            _state.update {
                it.copy(
                    error = (result as? AppResult.Failure)?.error?.let { error ->
                        (error as? AppError.Validation)?.message ?: "Action impossible"
                    },
                )
            }
        }
    }
}
