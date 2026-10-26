package com.mvp.feature.todo

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.location.GeoPosition
import com.mvp.core.common.domain.location.LocationAccuracy
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.data.repository.TodoRepositoryImpl
import com.mvp.feature.todo.data.source.InMemoryTodoLocalDataSource
import com.mvp.feature.todo.domain.model.LocationReminder
import com.mvp.feature.todo.domain.model.LocationReminderHere
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveLocationRemindersAvailableUseCase
import com.mvp.feature.todo.domain.usecase.RefreshLocationRemindersAvailabilityUseCase
import com.mvp.feature.todo.domain.usecase.RemoveLocationReminderUseCase
import com.mvp.feature.todo.domain.usecase.SetLocationReminderHereUseCase
import com.mvp.feature.todo.domain.usecase.SyncTodoLocationRemindersUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TodoLocationReminderUseCasesTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private var nextId = 0
    private val expiresAt = 7 * 24 * 3_600_000L
    private val repository = TodoRepositoryImpl(InMemoryTodoLocalDataSource(), idGenerator = { "id-${nextId++}" })
    private val here = GeoPosition(48.856614, 2.352222)
    private val location = FakeLocationProvider(AppResult.Success(here))
    private val scheduler = FakeLocationReminderScheduler()
    private val notifications = FakeNotificationsPreference(enabled = true)
    private val session = FakeSessionExpiry(expiresAt = expiresAt)

    private val analytics = FakeAnalyticsTracker()
    private val add = AddTodoUseCase(repository, analytics, dispatcher)
    private val toggle = ToggleTodoUseCase(repository, analytics, dispatcher)
    private val setHere = SetLocationReminderHereUseCase(repository, location, dispatcher)
    private val remove = RemoveLocationReminderUseCase(repository, dispatcher)
    private val sync = SyncTodoLocationRemindersUseCase(repository, notifications, session, scheduler, dispatcher)

    private fun failure(result: AppResult<*>): AppError = (result as AppResult.Failure).error

    @Test
    fun theReminderIsPlacedOnThePrecisePositionWithItsRadiusAndLabel() = scope.runTest {
        add("Imprimer le rapport")

        val result = setHere(LocationReminderHere("id-0", radiusMeters = 500, label = "  Bureau  "))

        val expected = Todo("id-0", "Imprimer le rapport", isDone = false, locationReminder = LocationReminder(here.latitude, here.longitude, 500, "Bureau"))
        assertEquals(AppResult.Success(expected), result)
        assertEquals(listOf(LocationAccuracy.Precise), location.requested)
    }

    @Test
    fun aBlankLabelGetsTheDefaultOne() = scope.runTest {
        add("Imprimer")

        val result = setHere(LocationReminderHere("id-0", label = "   "))

        assertEquals(LocationReminder.DEFAULT_LABEL, (result as AppResult.Success).data.locationReminder?.label)
        assertEquals(LocationReminder.DEFAULT_RADIUS, result.data.locationReminder?.radiusMeters)
    }

    @Test
    fun invalidRadiusOrLabelIsRejectedWithoutAskingForThePosition() = scope.runTest {
        add("Imprimer")

        assertIs<AppError.Validation>(failure(setHere(LocationReminderHere("id-0", radiusMeters = 50))))
        assertIs<AppError.Validation>(failure(setHere(LocationReminderHere("id-0", label = "a".repeat(LocationReminder.MAX_LABEL_LENGTH + 1)))))
        assertEquals(emptyList(), location.requested)
    }

    @Test
    fun aDoneOrUnknownTodoCannotGetAReminder() = scope.runTest {
        add("Imprimer")
        toggle("id-0")

        assertIs<AppError.Validation>(failure(setHere(LocationReminderHere("id-0"))))
        assertEquals(AppError.NotFound, failure(setHere(LocationReminderHere("inconnue"))))
    }

    @Test
    fun locationErrorsAreReturnedAndNothingIsSaved() = scope.runTest {
        add("Imprimer")
        location.result = AppResult.Failure(AppError.PermissionDenied)

        assertEquals(AppError.PermissionDenied, failure(setHere(LocationReminderHere("id-0"))))
        location.result = AppResult.Failure(AppError.Unavailable)
        assertEquals(AppError.Unavailable, failure(setHere(LocationReminderHere("id-0"))))
        assertNull(repository.observeTodos().first().single().locationReminder)
    }

    @Test
    fun atMostTwentyOpenTodosWithALocationReminder() = scope.runTest {
        repeat(LocationReminder.MAX_ACTIVE + 2) { add("Tâche $it") }
        repeat(LocationReminder.MAX_ACTIVE) { assertIs<AppResult.Success<Todo>>(setHere(LocationReminderHere("id-$it"))) }

        assertIs<AppError.Validation>(failure(setHere(LocationReminderHere("id-${LocationReminder.MAX_ACTIVE}"))))
        // Déplacer un rappel existant reste permis, et une tâche terminée libère sa place.
        assertIs<AppResult.Success<Todo>>(setHere(LocationReminderHere("id-0", radiusMeters = 100)))
        toggle("id-1")
        assertIs<AppResult.Success<Todo>>(setHere(LocationReminderHere("id-${LocationReminder.MAX_ACTIVE}")))
    }

    @Test
    fun removeClearsThePlace() = scope.runTest {
        add("Imprimer")
        setHere(LocationReminderHere("id-0"))

        assertEquals(AppResult.Success(Todo("id-0", "Imprimer", isDone = false)), remove("id-0"))
        assertEquals(AppResult.Failure(AppError.NotFound), remove("inconnue"))
    }

    @Test
    fun syncPlacesOnlyOpenTodosWithAPlaceUntilTheEndOfTheSession() = scope.runTest {
        add("Sans lieu")
        add("Au bureau")
        add("Terminée")
        setHere(LocationReminderHere("id-1"))
        setHere(LocationReminderHere("id-2"))
        toggle("id-2")

        val job = launch { sync(NoParams).collect {} }
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("id-1"), scheduler.scheduled.map { it.id })
        assertEquals(expiresAt, scheduler.expiresAt)

        // Cocher la tâche retire son rappel ; la décocher le repose.
        toggle("id-1")
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList(), scheduler.scheduled)
        toggle("id-1")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("id-1"), scheduler.scheduled.map { it.id })
        job.cancel()
    }

    @Test
    fun syncPlacesNothingWhenNotificationsAreOffOrPermissionsMissing() = scope.runTest {
        add("Au bureau")
        setHere(LocationReminderHere("id-0"))
        val job = launch { sync(NoParams).collect {} }
        testScheduler.advanceUntilIdle()
        assertEquals(1, scheduler.scheduled.size)

        notifications.enabled.value = false
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList(), scheduler.scheduled)

        notifications.enabled.value = true
        scheduler.available.value = false
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList(), scheduler.scheduled)

        scheduler.available.value = true
        testScheduler.advanceUntilIdle()
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(0, scheduler.cancelAllCalls)
        job.cancel()
    }

    @Test
    fun syncCancelsEverythingWithoutSession() = scope.runTest {
        add("Au bureau")
        setHere(LocationReminderHere("id-0"))
        val job = launch { sync(NoParams).collect {} }
        testScheduler.advanceUntilIdle()

        session.expiresAt.value = null
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), scheduler.scheduled)
        assertEquals(1, scheduler.cancelAllCalls)
        job.cancel()
    }

    @Test
    fun syncPlacesAtMostTwentyReminders() = scope.runTest {
        repeat(LocationReminder.MAX_ACTIVE + 3) { add("Tâche $it") }
        // Écrits directement : des lieux venus d'une ancienne version ne dépassent pas la limite du système.
        repeat(LocationReminder.MAX_ACTIVE + 3) { repository.setLocationReminder("id-$it", LocationReminder(48.0, 2.0, 200, "Lieu")) }

        val count = sync(NoParams).first()

        assertEquals(AppResult.Success(LocationReminder.MAX_ACTIVE), count)
        assertEquals((0 until LocationReminder.MAX_ACTIVE).map { "id-$it" }, scheduler.scheduled.map { it.id })
    }

    @Test
    fun aSchedulerFailureIsReportedAndRetriedOnTheNextChange() = scope.runTest {
        add("Au bureau")
        setHere(LocationReminderHere("id-0"))
        scheduler.failure = IllegalStateException("système indisponible")

        assertIs<AppError.Unknown>(failure(sync(NoParams).first()))

        scheduler.failure = null
        assertEquals(AppResult.Success(1), sync(NoParams).first())
    }

    @Test
    fun availabilityIsObservedAndRefreshedOnDemand() = scope.runTest {
        val observe = ObserveLocationRemindersAvailableUseCase(scheduler, dispatcher)
        val refresh = RefreshLocationRemindersAvailabilityUseCase(scheduler, dispatcher)

        assertEquals(AppResult.Success(true), observe(NoParams).first())
        scheduler.available.value = false
        assertEquals(AppResult.Success(false), observe(NoParams).first())
        assertEquals(AppResult.Success(Unit), refresh(NoParams))
        assertEquals(1, scheduler.refreshCalls)
    }
}
