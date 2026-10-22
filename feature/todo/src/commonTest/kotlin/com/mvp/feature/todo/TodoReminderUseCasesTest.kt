package com.mvp.feature.todo

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.data.repository.TodoRepositoryImpl
import com.mvp.feature.todo.data.source.InMemoryTodoLocalDataSource
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoReminder
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveRemindersEnabledUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoReminderUseCase
import com.mvp.feature.todo.domain.usecase.SyncTodoRemindersUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TodoReminderUseCasesTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private var nextId = 0
    private var now = 1_000_000L
    private val repository = TodoRepositoryImpl(InMemoryTodoLocalDataSource(), idGenerator = { "id-${nextId++}" })
    private val scheduler = FakeReminderScheduler()
    private val notifications = FakeNotificationsPreference(enabled = true)
    private val session = FakeSessionExpiry(expiresAt = now + 7 * DAY)

    private val analytics = FakeAnalyticsTracker()
    private val add = AddTodoUseCase(repository, analytics, dispatcher)
    private val toggle = ToggleTodoUseCase(repository, analytics, dispatcher)
    private val delete = DeleteTodoUseCase(repository, dispatcher)
    private val setReminder = SetTodoReminderUseCase(repository, { now }, dispatcher)
    private val sync = SyncTodoRemindersUseCase(repository, notifications, session, scheduler, { now }, dispatcher)

    @Test
    fun futureReminderIsSavedOnTheTodo() = scope.runTest {
        add("Appeler Paul")

        val result = setReminder(TodoReminder("id-0", at = now + 60_000))

        assertEquals(AppResult.Success(Todo("id-0", "Appeler Paul", isDone = false, reminderAt = now + 60_000)), result)
    }

    @Test
    fun pastOrPresentReminderIsRejected() = scope.runTest {
        add("Appeler Paul")

        assertIs<AppError.Validation>((setReminder(TodoReminder("id-0", at = now)) as AppResult.Failure).error)
        assertIs<AppError.Validation>((setReminder(TodoReminder("id-0", at = now - 1)) as AppResult.Failure).error)
    }

    @Test
    fun nullRemovesReminder() = scope.runTest {
        add("Appeler Paul")
        setReminder(TodoReminder("id-0", at = now + 60_000))

        assertEquals(AppResult.Success(Todo("id-0", "Appeler Paul", isDone = false)), setReminder(TodoReminder("id-0", at = null)))
    }

    @Test
    fun reminderOnUnknownTodoReturnsNotFound() = scope.runTest {
        assertEquals(AppResult.Failure(AppError.NotFound), setReminder(TodoReminder("inconnu", at = now + 60_000)))
    }

    @Test
    fun syncSchedulesOnlyUpcomingRemindersOfOpenTodos() = scope.runTest {
        add("Sans rappel")
        add("Rappel à venir")
        add("Rappel passé")
        add("Terminée")
        setReminder(TodoReminder("id-1", at = now + 60_000))
        setReminder(TodoReminder("id-2", at = now + 1_000))
        setReminder(TodoReminder("id-3", at = now + 60_000))
        toggle("id-3")
        now += 2_000

        val job = launch { sync(NoParams).collect {} }
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("id-1"), scheduler.scheduled.map { it.id })
    }

    @Test
    fun syncFollowsTodoChanges() = scope.runTest {
        add("Appeler Paul")
        val emissions = mutableListOf<AppResult<Int>>()
        val job = launch { sync(NoParams).collect { emissions += it } }
        testScheduler.advanceUntilIdle()

        setReminder(TodoReminder("id-0", at = now + 60_000))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("id-0"), scheduler.scheduled.map { it.id })

        // Terminée : plus de rappel ; rouverte : il revient.
        toggle("id-0")
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<Todo>(), scheduler.scheduled)
        toggle("id-0")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("id-0"), scheduler.scheduled.map { it.id })

        delete("id-0")
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(emptyList<Todo>(), scheduler.scheduled)
        assertEquals(listOf<AppResult<Int>>(AppResult.Success(0), AppResult.Success(1), AppResult.Success(0), AppResult.Success(1), AppResult.Success(0)), emissions)
    }

    @Test
    fun noReminderIsScheduledWhileNotificationsAreOff() = scope.runTest {
        add("Appeler Paul")
        setReminder(TodoReminder("id-0", at = now + 60_000))
        notifications.enabled.value = false

        val job = launch { sync(NoParams).collect {} }
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<Todo>(), scheduler.scheduled)

        notifications.enabled.value = true
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("id-0"), scheduler.scheduled.map { it.id })

        notifications.enabled.value = false
        testScheduler.advanceUntilIdle()
        job.cancel()
        assertEquals(emptyList<Todo>(), scheduler.scheduled)
    }

    @Test
    fun remindersAfterSessionEndAreNotScheduled() = scope.runTest {
        add("Avant la fin de session")
        add("Après la fin de session")
        setReminder(TodoReminder("id-0", at = now + DAY))
        setReminder(TodoReminder("id-1", at = now + 8 * DAY))

        val job = launch { sync(NoParams).collect {} }
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("id-0"), scheduler.scheduled.map { it.id })

        // Reconnexion : la nouvelle session couvre le second rappel.
        session.expiresAt.value = now + 14 * DAY
        testScheduler.advanceUntilIdle()
        job.cancel()
        assertEquals(listOf("id-0", "id-1"), scheduler.scheduled.map { it.id })
    }

    @Test
    fun everythingIsCancelledWithoutSession() = scope.runTest {
        add("Appeler Paul")
        setReminder(TodoReminder("id-0", at = now + DAY))
        val emissions = mutableListOf<AppResult<Int>>()
        val job = launch { sync(NoParams).collect { emissions += it } }
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("id-0"), scheduler.scheduled.map { it.id })

        // Session expirée (relue au lancement) : les tâches restent, mais aucun rappel ne doit s'afficher.
        session.expiresAt.value = null
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(emptyList<Todo>(), scheduler.scheduled)
        assertEquals(1, scheduler.cancelAllCalls)
        assertEquals(listOf<AppResult<Int>>(AppResult.Success(1), AppResult.Success(0)), emissions)
    }

    @Test
    fun schedulerFailureIsReportedWithoutStoppingSync() = scope.runTest {
        add("Appeler Paul")
        scheduler.failure = IllegalStateException("indisponible")
        val emissions = mutableListOf<AppResult<Int>>()
        val job = launch { sync(NoParams).collect { emissions += it } }
        testScheduler.advanceUntilIdle()

        scheduler.failure = null
        setReminder(TodoReminder("id-0", at = now + 60_000))
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertIs<AppResult.Failure>(emissions.first())
        assertEquals(AppResult.Success(1), emissions.last())
        assertEquals(listOf("id-0"), scheduler.scheduled.map { it.id })
    }

    @Test
    fun remindersEnabledFollowsNotificationsSetting() = scope.runTest {
        val observe = ObserveRemindersEnabledUseCase(notifications, dispatcher)
        assertEquals(AppResult.Success(true), observe(NoParams).first())

        notifications.enabled.value = false

        assertEquals(AppResult.Success(false), observe(NoParams).first())
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
