package com.mvp.feature.todo

import com.mvp.core.common.domain.analytics.AnalyticsTracker
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.data.repository.TodoRepositoryImpl
import com.mvp.feature.todo.data.source.InMemoryTodoLocalDataSource
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoMove
import com.mvp.feature.todo.domain.model.TodoPriority
import com.mvp.feature.todo.domain.model.TodoPriorityChange
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.MoveTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodosUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoPriorityUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TodoOrderingUseCasesTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private var nextId = 0
    private val local = InMemoryTodoLocalDataSource()
    private val repository = TodoRepositoryImpl(local, idGenerator = { "${nextId++}" })
    private val analytics: AnalyticsTracker = FakeAnalyticsTracker()

    private val observe = ObserveTodosUseCase(repository, dispatcher)
    private val add = AddTodoUseCase(repository, analytics, dispatcher)
    private val toggle = ToggleTodoUseCase(repository, analytics, dispatcher)
    private val move = MoveTodoUseCase(repository, dispatcher)
    private val setPriority = SetTodoPriorityUseCase(repository, dispatcher)

    private suspend fun ids(): List<String> = observe(NoParams).first().getOrNull()!!.map { it.id }

    private suspend fun todo(id: String): Todo = local.get(id)!!

    @Test
    fun newTodosGoAtTheBottom() = scope.runTest {
        add("Première")
        add("Deuxième")
        move(TodoMove("1", afterTodoId = null))

        add("Troisième")

        assertEquals(listOf("1", "0", "2"), ids())
    }

    @Test
    fun aTodoIsPlacedAfterTheOneAboveIt() = scope.runTest {
        repeat(4) { add("Tâche $it") }

        assertEquals(AppResult.Success(Unit), move(TodoMove("3", afterTodoId = "0")))
        assertEquals(listOf("0", "3", "1", "2"), ids())

        move(TodoMove("0", afterTodoId = "2"))
        assertEquals(listOf("3", "1", "2", "0"), ids())

        move(TodoMove("2", afterTodoId = null))
        assertEquals(listOf("2", "3", "1", "0"), ids())
    }

    @Test
    fun onlyTheMovedTodoGetsANewKey() = scope.runTest {
        repeat(3) { add("Tâche $it") }
        val before = listOf("0", "1").map { todo(it).position }

        move(TodoMove("2", afterTodoId = null))

        assertEquals(before, listOf("0", "1").map { todo(it).position })
    }

    @Test
    fun aTodoStaysInItsGroup() = scope.runTest {
        repeat(4) { add("Tâche $it") }
        toggle("2")
        toggle("3")
        assertEquals(listOf("0", "1", "2", "3"), ids())

        // Tâche à faire lâchée sous une terminée : en bas des tâches à faire.
        move(TodoMove("0", afterTodoId = "3"))
        assertEquals(listOf("1", "0", "2", "3"), ids())

        // Terminée lâchée sous une tâche à faire : en haut des terminées.
        move(TodoMove("3", afterTodoId = "1"))
        assertEquals(listOf("1", "0", "3", "2"), ids())
    }

    @Test
    fun movingOnAFilteredListPlacesTheTodoAfterItsVisibleNeighbour() = scope.runTest {
        repeat(5) { add("Tâche $it") }

        // Liste filtrée sur 0, 2 et 4 : 4 lâchée entre 0 et 2.
        move(TodoMove("4", afterTodoId = "0"))

        assertEquals(listOf("0", "4", "1", "2", "3"), ids())
    }

    @Test
    fun unknownTodosAreNotFoundAndDroppingOnItselfDoesNothing() = scope.runTest {
        add("Tâche")

        assertEquals(AppResult.Failure(AppError.NotFound), move(TodoMove("inconnue", afterTodoId = null)))
        assertEquals(AppResult.Failure(AppError.NotFound), move(TodoMove("0", afterTodoId = "inconnue")))
        assertEquals(AppResult.Success(Unit), move(TodoMove("0", afterTodoId = "0")))
    }

    @Test
    fun priorityIsSavedOnTheTodo() = scope.runTest {
        add("Tâche")

        val result = setPriority(TodoPriorityChange("0", TodoPriority.High))

        assertEquals(TodoPriority.High, result.getOrNull()?.priority)
        assertEquals(TodoPriority.High, todo("0").priority)
        assertEquals(AppResult.Failure(AppError.NotFound), setPriority(TodoPriorityChange("inconnue", TodoPriority.Low)))
    }

    @Test
    fun priorityLevelsRoundTrip() {
        TodoPriority.entries.forEach { assertEquals(it, TodoPriority.fromLevel(it.level)) }
        assertEquals(null, TodoPriority.fromLevel(4))
    }
}
