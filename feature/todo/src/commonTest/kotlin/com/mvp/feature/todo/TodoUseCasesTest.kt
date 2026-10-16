package com.mvp.feature.todo

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.data.repository.TodoRepositoryImpl
import com.mvp.feature.todo.data.source.InMemoryTodoLocalDataSource
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodosUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TodoUseCasesTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private var nextId = 0
    private val repository = TodoRepositoryImpl(InMemoryTodoLocalDataSource(), idGenerator = { "id-${nextId++}" })

    private val observe = ObserveTodosUseCase(repository, dispatcher)
    private val add = AddTodoUseCase(repository, dispatcher)
    private val toggle = ToggleTodoUseCase(repository, dispatcher)
    private val delete = DeleteTodoUseCase(repository, dispatcher)

    private suspend fun currentTodos(): List<Todo> = observe(NoParams).first().getOrNull()!!

    @Test
    fun addTrimsTitleAndExposesIt() = scope.runTest {
        add("  Acheter du pain  ")

        assertEquals(listOf(Todo("id-0", "Acheter du pain", isDone = false)), currentTodos())
    }

    @Test
    fun blankTitleIsRejected() = scope.runTest {
        val result = add("   ")

        assertIs<AppError.Validation>((result as AppResult.Failure).error)
        assertEquals(emptyList(), currentTodos())
    }

    @Test
    fun tooLongTitleIsRejected() = scope.runTest {
        val result = add("x".repeat(AddTodoUseCase.MAX_TITLE_LENGTH + 1))

        assertIs<AppError.Validation>((result as AppResult.Failure).error)
    }

    @Test
    fun doneTodosAreListedLast() = scope.runTest {
        add("Première")
        add("Deuxième")
        toggle("id-0")

        assertEquals(listOf("Deuxième", "Première"), currentTodos().map { it.title })
    }

    @Test
    fun toggleUnknownTodoReturnsNotFound() = scope.runTest {
        assertEquals(AppResult.Failure(AppError.NotFound), toggle("inconnu"))
    }

    @Test
    fun deleteRemovesTodo() = scope.runTest {
        add("À supprimer")

        assertEquals(AppResult.Success(Unit), delete("id-0"))
        assertEquals(emptyList(), currentTodos())
        assertEquals(AppResult.Failure(AppError.NotFound), delete("id-0"))
    }
}
