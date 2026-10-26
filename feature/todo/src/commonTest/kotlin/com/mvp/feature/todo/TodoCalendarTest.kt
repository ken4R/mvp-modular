package com.mvp.feature.todo

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.deeplink.domain.DeepLinks
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.feature.todo.data.calendar.SystemDayCalendar
import com.mvp.feature.todo.data.repository.TodoRepositoryImpl
import com.mvp.feature.todo.data.source.InMemoryTodoLocalDataSource
import com.mvp.feature.todo.domain.calendar.CalendarDate
import com.mvp.feature.todo.domain.calendar.DayCalendar
import com.mvp.feature.todo.domain.model.CalendarMonth
import com.mvp.feature.todo.domain.model.CalendarMonthRequest
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoCalendarEvent
import com.mvp.feature.todo.domain.model.TodoDueDate
import com.mvp.feature.todo.domain.usecase.ExportTodoToCalendarUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoCalendarUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoDueDateUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/** Calendrier des tâches par date d'échéance et export vers le calendrier du téléphone (ADR 0022). */
class TodoCalendarTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val local = InMemoryTodoLocalDataSource()
    private val repository = TodoRepositoryImpl(local)

    /** Lundi 26 octobre 2026, à midi (jours UTC du calendrier de test). */
    private var now = day(2026, 10, 26) * DAY + 12 * HOUR
    private val observe = ObserveTodoCalendarUseCase(repository, UtcDayCalendar, { now }, dispatcher)
    private val setDueDate = SetTodoDueDateUseCase(repository, dispatcher)
    private val export = ExportTodoToCalendarUseCase(repository, dispatcher)

    private suspend fun month(year: Int, month: Int): CalendarMonth =
        observe(CalendarMonthRequest(year, month)).first().getOrNull()!!

    @Test
    fun calendarDateMatchesKnownDaysAndWeekdays() {
        assertEquals(0, CalendarDate(1970, 1, 1).epochDay)
        assertEquals(10_957, CalendarDate(2000, 1, 1).epochDay)
        assertEquals(20_752, CalendarDate(2026, 10, 26).epochDay)
        assertEquals(CalendarDate(2024, 2, 29), CalendarDate.fromEpochDay(CalendarDate(2024, 2, 29).epochDay))
        assertEquals(CalendarDate(1969, 12, 31), CalendarDate.fromEpochDay(-1))
        assertEquals(4, CalendarDate(1970, 1, 1).isoDayOfWeek) // jeudi
        assertEquals(1, CalendarDate(2026, 10, 26).isoDayOfWeek) // lundi
        assertEquals(7, CalendarDate(2026, 11, 1).isoDayOfWeek) // dimanche
        assertEquals(29, CalendarDate.daysInMonth(2024, 2))
        assertEquals(28, CalendarDate.daysInMonth(2100, 2))
        assertEquals(29, CalendarDate.daysInMonth(2000, 2))
        assertEquals(31, CalendarDate.daysInMonth(2026, 12))
        // Aller-retour sur toute la plage des échéances.
        for (epochDay in CalendarDate.MIN_EPOCH_DAY..CalendarDate.MAX_EPOCH_DAY) {
            assertEquals(epochDay, CalendarDate.fromEpochDay(epochDay).epochDay)
        }
    }

    @Test
    fun monthIsLaidOutInWholeWeeksFromMondayToSunday() = scope.runTest {
        val october = month(2026, 10)

        // Le 1er octobre 2026 est un jeudi : la grille commence le lundi 28 septembre, 5 semaines.
        assertEquals(35, october.days.size)
        assertEquals(CalendarDate(2026, 9, 28), october.days.first().date)
        assertEquals(CalendarDate(2026, 11, 1), october.days.last().date)
        assertEquals(31, october.days.count { it.isInMonth })
        assertFalse(october.days.first().isInMonth)
        assertEquals(listOf(CalendarDate(2026, 10, 26)), october.days.filter { it.isToday }.map { it.date })
        assertEquals(CalendarDate(2026, 10, 26), october.today)
        // Février 2027 commence un lundi et compte 28 jours : 4 semaines tout juste.
        assertEquals(28, month(2027, 2).days.size)
        // Août 2027 commence un dimanche : 6 semaines.
        assertEquals(42, month(2027, 8).days.size)
    }

    @Test
    fun todosAreShownOnTheirDueDatePendingFirstWithOverdueAndUndatedCounts() = scope.runTest {
        local.upsert(Todo("done", "Faite", isDone = true, dueDate = day(2026, 10, 28)))
        local.upsert(Todo("a", "Appeler Paul", isDone = false, dueDate = day(2026, 10, 28)))
        local.upsert(Todo("late", "En retard", isDone = false, dueDate = day(2026, 10, 20)))
        local.upsert(Todo("past-done", "Faite à temps", isDone = true, dueDate = day(2026, 10, 21)))
        local.upsert(Todo("next", "Le mois prochain", isDone = false, dueDate = day(2026, 11, 1)))
        local.upsert(Todo("far", "Plus tard", isDone = false, dueDate = day(2026, 12, 15)))
        local.upsert(Todo("none", "Sans échéance", isDone = false))

        val october = month(2026, 10)
        fun on(date: CalendarDate) = october.days.single { it.date == date }

        assertEquals(listOf("a", "done"), on(CalendarDate(2026, 10, 28)).todos.map { it.id })
        assertEquals(1, on(CalendarDate(2026, 10, 28)).pendingCount)
        assertTrue(on(CalendarDate(2026, 10, 20)).isOverdue)
        assertFalse(on(CalendarDate(2026, 10, 21)).isOverdue)
        assertFalse(on(CalendarDate(2026, 10, 28)).isOverdue)
        // Le 1er novembre complète la dernière semaine : sa tâche y est, hors du mois.
        assertEquals(listOf("next"), on(CalendarDate(2026, 11, 1)).todos.map { it.id })
        assertEquals(1, october.overdueCount)
        assertEquals(1, october.undatedCount)
        // Le 15 décembre est hors de la grille, la tâche sans échéance n'y est pas.
        assertEquals(5, october.days.sumOf { it.todos.size })
    }

    @Test
    fun recomputesWhenTodosChangeAndMovesTodayAtMidnight() = scope.runTest {
        // Dimanche 25 octobre, 23 h ; l'heure avance avec le temps virtuel du test.
        val start = day(2026, 10, 25) * DAY + 23 * HOUR
        local.upsert(Todo("a", "Tâche", isDone = false, dueDate = day(2026, 10, 25)))
        val ticking = ObserveTodoCalendarUseCase(repository, UtcDayCalendar, { start + testScheduler.currentTime }, dispatcher)

        val (before, after) = ticking(CalendarMonthRequest(2026, 10)).take(2).toList().map { it.getOrNull()!! }

        assertEquals(CalendarDate(2026, 10, 25), before.today)
        assertEquals(0, before.overdueCount)
        assertEquals(CalendarDate(2026, 10, 26), after.today)
        assertEquals(1, after.overdueCount)
        // Vérifié chaque minute, réémis seulement au changement de jour.
        assertEquals(HOUR, testScheduler.currentTime)

        local.setDueDate("a", null)
        assertEquals(1, month(2026, 10).undatedCount)
    }

    @Test
    fun overdueRuleIsAPendingTodoDueBeforeToday() {
        val today = day(2026, 10, 26)
        assertTrue(Todo("a", "A", isDone = false, dueDate = today - 1).isOverdue(today))
        assertFalse(Todo("a", "A", isDone = false, dueDate = today).isOverdue(today))
        assertFalse(Todo("a", "A", isDone = true, dueDate = today - 1).isOverdue(today))
        assertFalse(Todo("a", "A", isDone = false).isOverdue(today))
    }

    @Test
    fun monthOutsideTheCalendarIsRejected() = scope.runTest {
        for (request in listOf(CalendarMonthRequest(2026, 0), CalendarMonthRequest(2026, 13), CalendarMonthRequest(1999, 12))) {
            val result = observe(request).first()
            assertIs<AppError.Validation>((result as AppResult.Failure).error)
        }
        assertEquals(CalendarMonthRequest(2027, 1), CalendarMonthRequest(2026, 12).next())
        assertEquals(CalendarMonthRequest(2025, 12), CalendarMonthRequest(2026, 1).previous())
    }

    @Test
    fun dueDateIsSavedRemovedAndValidated() = scope.runTest {
        local.upsert(Todo("a", "Tâche", isDone = false))

        assertEquals(
            AppResult.Success(Todo("a", "Tâche", isDone = false, dueDate = day(2026, 10, 28))),
            setDueDate(TodoDueDate("a", day(2026, 10, 28))),
        )
        // Une date passée est acceptée : la tâche est déjà en retard.
        assertEquals(day(2026, 10, 1), setDueDate(TodoDueDate("a", day(2026, 10, 1))).getOrNull()?.dueDate)
        assertEquals(null, setDueDate(TodoDueDate("a", null)).getOrNull()?.dueDate)
        assertIs<AppError.Validation>((setDueDate(TodoDueDate("a", day(1999, 12, 31))) as AppResult.Failure).error)
        assertIs<AppError.Validation>((setDueDate(TodoDueDate("a", day(2101, 1, 1))) as AppResult.Failure).error)
        assertEquals(AppResult.Failure(AppError.NotFound), setDueDate(TodoDueDate("inconnue", day(2026, 10, 28))))
    }

    @Test
    fun exportIsAnAllDayEventWithTheTitleAndLinkButNeverTheNote() = scope.runTest {
        local.upsert(Todo("a", "Appeler\n  Paul ", isDone = false, note = "Code 1234", dueDate = day(2026, 10, 28)))
        local.upsert(Todo("b", "Sans échéance", isDone = false))

        assertEquals(
            AppResult.Success(
                TodoCalendarEvent("Appeler Paul", CalendarDate(2026, 10, 28), DeepLinks.url(DeepLink.Tasks("a"))),
            ),
            export("a"),
        )
        assertIs<AppError.Validation>((export("b") as AppResult.Failure).error)
        assertEquals(AppResult.Failure(AppError.NotFound), export("inconnue"))
    }

    @Test
    @OptIn(ExperimentalTime::class)
    fun systemCalendarCountsLocalDaysAcrossDaylightSavingTime() {
        val paris = TimeZone.of("Europe/Paris")
        val calendar = SystemDayCalendar { paris }
        fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0) =
            LocalDateTime(year, month, day, hour, minute).toInstant(paris).toEpochMilliseconds()

        // Minuit à Paris, c'est encore la veille en UTC : le jour est celui du fuseau.
        assertEquals(day(2026, 10, 26), calendar.dayOf(at(2026, 10, 26, 0, 30)))
        assertEquals(day(2026, 10, 25), calendar.dayOf(at(2026, 10, 25, 23, 59)))
        assertEquals(at(2026, 10, 26), calendar.startOfDay(day(2026, 10, 26)))
        // Retour à l'heure d'hiver le dimanche 25 octobre 2026 : ce jour-là dure 25 h.
        assertEquals(25 * HOUR, calendar.startOfDay(day(2026, 10, 26)) - calendar.startOfDay(day(2026, 10, 25)))
    }

    private fun day(year: Int, month: Int, day: Int): Int = CalendarDate(year, month, day).epochDay

    /** Jours UTC : le jour d'un instant est sa division par 24 h. */
    private object UtcDayCalendar : DayCalendar {
        override fun dayOf(instant: Long): Int = instant.floorDiv(DAY).toInt()
        override fun startOfDay(epochDay: Int): Long = epochDay * DAY
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
