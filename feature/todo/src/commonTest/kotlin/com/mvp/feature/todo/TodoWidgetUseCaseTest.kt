package com.mvp.feature.todo

import com.mvp.core.common.domain.applock.AppLockPreference
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.data.repository.TodoRepositoryImpl
import com.mvp.feature.todo.data.source.InMemoryTodoLocalDataSource
import com.mvp.feature.todo.domain.model.TodoPriority
import com.mvp.feature.todo.domain.model.TodoPriorityChange
import com.mvp.feature.todo.domain.model.TodoReminder
import com.mvp.feature.todo.domain.model.TodoWidgetItem
import com.mvp.feature.todo.domain.model.TodoWidgetState
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoWidgetUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoPriorityUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoReminderUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TodoWidgetUseCaseTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private var nextId = 0
    private var now = 1_000_000L
    private val repository = TodoRepositoryImpl(InMemoryTodoLocalDataSource(), idGenerator = { "id-${nextId++}" })
    private val session = FakeSessionExpiry(expiresAt = now + DAY)
    private val appLock = FakeAppLockPreference(enabled = false)

    private val add = AddTodoUseCase(repository, FakeAnalyticsTracker(), dispatcher)
    private val toggle = ToggleTodoUseCase(repository, FakeAnalyticsTracker(), dispatcher)
    private val setReminder = SetTodoReminderUseCase(repository, { now }, dispatcher)
    private val setPriority = SetTodoPriorityUseCase(repository, dispatcher)
    private val delete = DeleteTodoUseCase(repository, dispatcher)
    private val widget = ObserveTodoWidgetUseCase(repository, session, appLock, { now }, dispatcher)

    private suspend fun current(): TodoWidgetState =
        (widget(NoParams).first() as AppResult.Success).data

    @Test
    fun showsRemainingTodosWithTheNearestReminderFirst() = scope.runTest {
        add("Sans rappel")
        add("Rappel lointain")
        add("Rappel proche")
        add("Terminée")
        setReminder(TodoReminder("id-1", at = now + 60 * MINUTE))
        setReminder(TodoReminder("id-2", at = now + MINUTE))
        toggle("id-3")

        val state = assertIs<TodoWidgetState.Tasks>(current())

        assertEquals(3, state.remainingCount)
        assertEquals(
            listOf(
                TodoWidgetItem("id-2", "Rappel proche", now + MINUTE, "mvpmodular://tasks/id-2"),
                TodoWidgetItem("id-1", "Rappel lointain", now + 60 * MINUTE, "mvpmodular://tasks/id-1"),
                TodoWidgetItem("id-0", "Sans rappel", null, "mvpmodular://tasks/id-0"),
            ),
            state.items,
        )
        assertEquals(now + DAY, state.visibleUntil)
        assertEquals("mvpmodular://tasks", state.link)
    }

    @Test
    fun withoutReminderHigherPriorityComesFirstThenTheManualOrder() = scope.runTest {
        add("Basse")
        add("Aucune")
        add("Haute")
        add("Avec rappel")
        setPriority(TodoPriorityChange("id-0", TodoPriority.Low))
        setPriority(TodoPriorityChange("id-2", TodoPriority.High))
        setReminder(TodoReminder("id-3", at = now + MINUTE))

        val state = assertIs<TodoWidgetState.Tasks>(current())

        assertEquals(listOf("id-3", "id-2", "id-0", "id-1"), state.items.map { it.id })
    }

    @Test
    fun todosInTheTrashAreNeitherShownNorCounted() = scope.runTest {
        add("Appeler Paul")
        add("Supprimée")
        delete("id-1")

        val state = assertIs<TodoWidgetState.Tasks>(current())

        assertEquals(1, state.remainingCount)
        assertEquals(listOf("id-0"), state.items.map { it.id })
    }

    @Test
    fun keepsAtMostFiveItemsButCountsThemAll() = scope.runTest {
        repeat(7) { add("Tâche $it") }

        val state = assertIs<TodoWidgetState.Tasks>(current())

        assertEquals(7, state.remainingCount)
        assertEquals(listOf("id-0", "id-1", "id-2", "id-3", "id-4"), state.items.map { it.id })
    }

    @Test
    fun nothingIsShownWithoutSession() = scope.runTest {
        add("Appeler Paul")
        session.expiresAt.value = null

        assertEquals(TodoWidgetState.SignedOut, current())
    }

    @Test
    fun nothingIsShownOnceTheSessionHasExpired() = scope.runTest {
        add("Appeler Paul")
        now += DAY

        assertEquals(TodoWidgetState.SignedOut, current())
    }

    @Test
    fun widgetIsEmptiedWhenTheSessionEndsWhileObserved() = scope.runTest {
        add("Appeler Paul")
        val states = mutableListOf<TodoWidgetState>()
        val job = launch { widget(NoParams).collect { states += (it as AppResult.Success).data } }
        testScheduler.advanceTimeBy(DAY - 1)
        testScheduler.runCurrent()
        assertIs<TodoWidgetState.Tasks>(states.single())

        testScheduler.advanceTimeBy(2)
        testScheduler.runCurrent()
        job.cancel()
        assertEquals(TodoWidgetState.SignedOut, states.last())
    }

    @Test
    fun nothingIsShownWhileTheAppLockIsOn() = scope.runTest {
        add("Appeler Paul")
        appLock.enabled.value = true

        assertEquals(TodoWidgetState.Locked, current())
    }

    @Test
    fun followsLogoutAndAppLockChanges() = scope.runTest {
        add("Appeler Paul")
        val states = mutableListOf<TodoWidgetState>()
        val job = launch { widget(NoParams).collect { states += (it as AppResult.Success).data } }
        testScheduler.runCurrent()

        appLock.enabled.value = true
        testScheduler.runCurrent()
        appLock.enabled.value = false
        testScheduler.runCurrent()
        session.expiresAt.value = null
        testScheduler.runCurrent()
        job.cancel()

        val tasks = TodoWidgetState.Tasks(
            remainingCount = 1,
            items = listOf(TodoWidgetItem("id-0", "Appeler Paul", null, "mvpmodular://tasks/id-0")),
            visibleUntil = now + DAY,
            link = "mvpmodular://tasks",
        )
        assertEquals(listOf(tasks, TodoWidgetState.Locked, tasks, TodoWidgetState.SignedOut), states)
    }

    @Test
    fun sourceErrorIsReportedAsFailure() = scope.runTest {
        val failing = ObserveTodoWidgetUseCase(
            repository = repository,
            session = FakeSessionExpiry(expiresAt = now + DAY),
            appLock = object : AppLockPreference {
                override fun observeAppLockEnabled(): Flow<Boolean> = flow { error("lecture impossible") }
            },
            now = { now },
            dispatcher = dispatcher,
        )

        assertIs<AppResult.Failure>(failing(NoParams).first())
    }

    private companion object {
        const val MINUTE = 60 * 1000L
        const val DAY = 24 * 60 * MINUTE
    }
}
