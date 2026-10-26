package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.feature.todo.domain.calendar.CalendarDate
import com.mvp.feature.todo.domain.calendar.DayCalendar
import com.mvp.feature.todo.domain.model.CalendarDay
import com.mvp.feature.todo.domain.model.CalendarMonth
import com.mvp.feature.todo.domain.model.CalendarMonthRequest
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Calendrier des tâches par date d'échéance (ADR 0022) : le mois demandé en semaines du lundi au dimanche, avec les
 * tâches de chaque jour. Recalculé à chaque changement des tâches et à minuit (aujourd'hui, retards), même si l'écran
 * reste ouvert. Un mois hors de 2000-2100 renvoie [AppError.Validation]. [now] : heure courante (epoch ms).
 */
class ObserveTodoCalendarUseCase(
    private val repository: TodoRepository,
    private val calendar: DayCalendar,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<CalendarMonthRequest, CalendarMonth>(dispatcher) {

    override fun execute(params: CalendarMonthRequest): Flow<AppResult<CalendarMonth>> {
        if (params.month !in 1..12 || params.year !in CalendarDate.MIN_YEAR..CalendarDate.MAX_YEAR) {
            return flowOf(AppResult.Failure(AppError.Validation("Mois hors du calendrier")))
        }
        return combine(repository.observeTodos(), dayChanges()) { todos, today ->
            AppResult.Success(month(params, todos, today))
        }
    }

    /** Aujourd'hui tout de suite, puis à chaque minuit local. */
    private fun dayChanges(): Flow<Int> = flow {
        while (true) {
            val current = now()
            val today = calendar.dayOf(current)
            emit(today)
            // Au moins 1 ms : un calendrier qui ne trouverait pas de jour suivant ne fait pas tourner la boucle à vide.
            delay(maxOf(calendar.startOfDay(today + 1) - current, 1L))
        }
    }.distinctUntilChanged()

    private fun month(request: CalendarMonthRequest, todos: List<Todo>, today: Int): CalendarMonth {
        val first = CalendarDate(request.year, request.month, 1)
        val gridStart = first.epochDay - (first.isoDayOfWeek - 1)
        val leading = first.epochDay - gridStart
        val weeks = (leading + CalendarDate.daysInMonth(request.year, request.month) + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK
        // Tâches à faire d'abord, chaque groupe dans l'ordre de la liste (sortedBy est stable).
        val byDay = todos.filter { it.dueDate != null }.sortedBy { it.isDone }.groupBy { it.dueDate!! }
        val days = (gridStart until gridStart + weeks * DAYS_PER_WEEK).map { epochDay ->
            val date = CalendarDate.fromEpochDay(epochDay)
            val dayTodos = byDay[epochDay].orEmpty()
            CalendarDay(
                date = date,
                epochDay = epochDay,
                isInMonth = date.month == request.month,
                isToday = epochDay == today,
                isOverdue = epochDay < today && dayTodos.any { !it.isDone },
                todos = dayTodos,
            )
        }
        return CalendarMonth(
            year = request.year,
            month = request.month,
            days = days,
            today = CalendarDate.fromEpochDay(today),
            overdueCount = todos.count { todo -> !todo.isDone && todo.dueDate?.let { it < today } == true },
            undatedCount = todos.count { it.dueDate == null },
        )
    }

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}
