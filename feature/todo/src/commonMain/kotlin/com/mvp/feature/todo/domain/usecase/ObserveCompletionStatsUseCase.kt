package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.calendar.WeekCalendar
import com.mvp.feature.todo.domain.model.CompletionStats
import com.mvp.feature.todo.domain.model.WeekCompletion
import com.mvp.feature.todo.domain.repository.TodoStatsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Tâches terminées par semaine sur les [WEEK_COUNT] dernières semaines (ADR 0019), recalculées à chaque changement
 * des tâches. Une tâche terminée hors de ces semaines (plus ancienne, ou datée dans le futur par une horloge
 * déréglée) ne compte pas. [now] : heure courante (epoch ms), lue à chaque calcul.
 */
class ObserveCompletionStatsUseCase(
    private val repository: TodoStatsRepository,
    private val calendar: WeekCalendar,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, CompletionStats>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<CompletionStats>> =
        repository.observeCompletionTimes().map { times -> AppResult.Success(stats(times)) }

    private fun stats(times: List<Long>): CompletionStats {
        val currentWeek = calendar.weekStartOf(now())
        val starts = (WEEK_COUNT - 1 downTo 0).map { calendar.shiftWeeks(currentWeek, -it) }
        val end = calendar.shiftWeeks(currentWeek, 1)
        val counts = IntArray(WEEK_COUNT)
        times.forEach { time ->
            if (time < starts.first() || time >= end) return@forEach
            counts[starts.indexOfLast { it <= time }]++
        }
        val weeks = starts.mapIndexed { index, start -> WeekCompletion(start, counts[index]) }
        return CompletionStats(weeks = weeks, total = counts.sum(), best = counts.max())
    }

    companion object {
        /** Semaine en cours comprise : deux mois environ. */
        const val WEEK_COUNT = 8
    }
}
