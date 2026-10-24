package com.mvp.feature.todo

import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.data.stats.SystemWeekCalendar
import com.mvp.feature.todo.domain.calendar.WeekCalendar
import com.mvp.feature.todo.domain.model.CompletionStats
import com.mvp.feature.todo.domain.model.WeekCompletion
import com.mvp.feature.todo.domain.repository.TodoStatsRepository
import com.mvp.feature.todo.domain.usecase.ObserveCompletionStatsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

class CompletionStatsTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val times = MutableStateFlow<List<Long>>(emptyList())
    private val repository = object : TodoStatsRepository {
        override fun observeCompletionTimes(): Flow<List<Long>> = times
    }

    /** Mercredi de la 10ᵉ semaine du calendrier de test, à midi. */
    private var now = FixedWeekCalendar.weekStart(10) + 2 * DAY + 12 * HOUR
    private val observe = ObserveCompletionStatsUseCase(repository, FixedWeekCalendar, { now }, dispatcher)

    private suspend fun stats(): CompletionStats = observe(NoParams).first().getOrNull()!!

    @Test
    fun withoutCompletedTodoEveryWeekIsZero() = scope.runTest {
        val stats = stats()

        assertEquals((3..10).map { WeekCompletion(FixedWeekCalendar.weekStart(it), 0) }, stats.weeks)
        assertEquals(0, stats.total)
        assertEquals(0, stats.best)
    }

    @Test
    fun countsTodosPerWeekFromTheOldestToTheCurrentOne() = scope.runTest {
        times.value = listOf(
            FixedWeekCalendar.weekStart(10), // lundi 00:00 de la semaine en cours
            FixedWeekCalendar.weekStart(10) + DAY,
            FixedWeekCalendar.weekStart(10) - 1, // dimanche 23:59:59.999 : semaine précédente
            FixedWeekCalendar.weekStart(3), // première semaine affichée
            FixedWeekCalendar.weekStart(6) + 3 * DAY,
        )

        val stats = stats()

        assertEquals(listOf(1, 0, 0, 1, 0, 0, 1, 2), stats.weeks.map { it.count })
        assertEquals(5, stats.total)
        assertEquals(2, stats.best)
    }

    @Test
    fun ignoresTodosOlderThanTheWindowOrDatedInTheFuture() = scope.runTest {
        times.value = listOf(
            FixedWeekCalendar.weekStart(3) - 1,
            FixedWeekCalendar.weekStart(11),
            FixedWeekCalendar.weekStart(10) + 6 * DAY + 23 * HOUR,
        )

        val stats = stats()

        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 1), stats.weeks.map { it.count })
        assertEquals(1, stats.total)
    }

    @Test
    fun recomputesWhenTodosChange() = scope.runTest {
        times.value = listOf(now)
        assertEquals(1, stats().total)

        times.value = emptyList()
        assertEquals(0, stats().total)
    }

    @Test
    @OptIn(ExperimentalTime::class)
    fun systemCalendarStartsWeeksOnMondayMidnightAcrossDaylightSavingTime() {
        val paris = TimeZone.of("Europe/Paris")
        val calendar = SystemWeekCalendar { paris }
        fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0) =
            LocalDateTime(year, month, day, hour, minute).toInstant(paris).toEpochMilliseconds()

        // Passage à l'heure d'été le dimanche 29 mars 2026 : la semaine du 23 mars dure 167 h.
        assertEquals(at(2026, 3, 23), calendar.weekStartOf(at(2026, 3, 29, 23, 30)))
        assertEquals(at(2026, 3, 30), calendar.weekStartOf(at(2026, 3, 30)))
        assertEquals(at(2026, 3, 30), calendar.weekStartOf(at(2026, 4, 1, 10)))
        assertEquals(at(2026, 3, 23), calendar.shiftWeeks(at(2026, 3, 30), -1))
        assertEquals(at(2026, 3, 30), calendar.shiftWeeks(at(2026, 3, 23), 1))
        // Retour à l'heure d'hiver le dimanche 25 octobre 2026 : la semaine du 19 octobre dure 169 h.
        assertEquals(at(2026, 10, 26), calendar.shiftWeeks(at(2026, 10, 19), 1))
        assertEquals(at(2026, 10, 19), calendar.weekStartOf(at(2026, 10, 25, 23, 59)))
    }
}

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR
private const val WEEK = 7 * DAY

/** Semaines de 7 × 24 h sans fuseau, la semaine 0 commençant à l'heure 0. */
private object FixedWeekCalendar : WeekCalendar {
    fun weekStart(index: Int): Long = index * WEEK

    override fun weekStartOf(instant: Long): Long = instant.floorDiv(WEEK) * WEEK

    override fun shiftWeeks(weekStart: Long, weeks: Int): Long = weekStart + weeks * WEEK
}
