package com.mvp.android.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.todo.domain.calendar.CalendarDate
import com.mvp.feature.todo.domain.model.CalendarDay
import com.mvp.feature.todo.domain.model.CalendarMonth
import com.mvp.feature.todo.domain.model.CalendarMonthRequest
import com.mvp.feature.todo.domain.usecase.ObserveTodoCalendarUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * [month] : `null` tant que le premier calcul du mois [request] n'est pas arrivé. [selectedDay] : jour choisi
 * (jours depuis le 1970-01-01), dont les tâches sont listées sous la grille.
 */
data class TodoCalendarUiState(
    val request: CalendarMonthRequest,
    val month: CalendarMonth? = null,
    val selectedDay: Int,
    val error: Boolean = false,
) {
    val selected: CalendarDay? get() = month?.days?.firstOrNull { it.epochDay == selectedDay }
    val canShowPrevious: Boolean get() = request.previous().year >= CalendarDate.MIN_YEAR
    val canShowNext: Boolean get() = request.next().year <= CalendarDate.MAX_YEAR
}

/** Calendrier des tâches par échéance (ADR 0022) : le mois est calculé dans KMP, ici seulement l'état de l'écran. */
class TodoCalendarViewModel(
    private val observeCalendar: ObserveTodoCalendarUseCase,
    private val toggleTodo: ToggleTodoUseCase,
) : ViewModel() {

    private val _state: MutableStateFlow<TodoCalendarUiState>
    val state: StateFlow<TodoCalendarUiState>
    private var observer: Job? = null

    init {
        val today = LocalDate.now()
        _state = MutableStateFlow(
            TodoCalendarUiState(CalendarMonthRequest(today.year, today.monthValue), selectedDay = today.toEpochDay().toInt()),
        )
        state = _state.asStateFlow()
        observe(_state.value.request)
    }

    fun onPreviousMonth() = showMonth(_state.value.request.previous())

    fun onNextMonth() = showMonth(_state.value.request.next())

    /** Revient au mois en cours et choisit aujourd'hui (celui du calcul KMP, recalculé à minuit). */
    fun onToday() {
        val today = _state.value.month?.today ?: LocalDate.now().let { CalendarDate(it.year, it.monthValue, it.dayOfMonth) }
        showMonth(CalendarMonthRequest(today.year, today.month), selectedDay = today.epochDay)
    }

    /** Un jour du mois précédent ou suivant (début ou fin de la grille) affiche ce mois-là. */
    fun onSelectDay(day: CalendarDay) {
        if (day.isInMonth) {
            _state.update { it.copy(selectedDay = day.epochDay) }
        } else {
            showMonth(CalendarMonthRequest(day.date.year, day.date.month), selectedDay = day.epochDay)
        }
    }

    fun onToggle(id: String) = viewModelScope.launch { toggleTodo(id) }

    /** Sans jour précisé : aujourd'hui pour le mois en cours, sinon le 1er du mois. */
    private fun showMonth(request: CalendarMonthRequest, selectedDay: Int? = null) {
        if (request.year !in CalendarDate.MIN_YEAR..CalendarDate.MAX_YEAR) return
        val today = _state.value.month?.today
        val day = selectedDay
            ?: today?.takeIf { it.year == request.year && it.month == request.month }?.epochDay
            ?: CalendarDate(request.year, request.month, 1).epochDay
        // L'ancien mois est retiré : la grille n'affiche jamais un mois sous le titre d'un autre.
        _state.update { it.copy(request = request, month = null, selectedDay = day, error = false) }
        observe(request)
    }

    private fun observe(request: CalendarMonthRequest) {
        observer?.cancel()
        observer = observeCalendar(request)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> _state.update { it.copy(month = result.data, error = false) }
                    is AppResult.Failure -> _state.update { it.copy(error = true) }
                }
            }
            .launchIn(viewModelScope)
    }
}
