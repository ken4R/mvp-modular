package com.mvp.feature.todo

import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.FilteredTodos
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoFilter
import com.mvp.feature.todo.domain.model.TodoAttachment
import com.mvp.feature.todo.domain.model.TodoStatusFilter
import com.mvp.feature.todo.domain.repository.TodoRepository
import com.mvp.feature.todo.domain.usecase.ObserveFilteredTodosUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TodoFilterUseCaseTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val todos = listOf(
        Todo("courses", "Faire les courses", isDone = true, note = "Crème fraîche, œufs"),
        Todo("dentiste", "Appeler le dentiste", isDone = false, reminderAt = 5_000L),
        Todo("facture", "Payer la facture EDF", isDone = false, attachmentCount = 1),
        Todo("tache", "Tâche sans détail", isDone = false),
    )
    private val repository = FixedTodoRepository(todos)
    private val observe = ObserveFilteredTodosUseCase(repository, dispatcher)

    private suspend fun filtered(filter: TodoFilter): FilteredTodos = observe(filter).first().getOrNull()!!

    private suspend fun ids(filter: TodoFilter): List<String> = filtered(filter).todos.map { it.id }

    @Test
    fun noFilterKeepsEveryTodoPendingFirst() = scope.runTest {
        val result = filtered(TodoFilter())

        assertEquals(listOf("dentiste", "facture", "tache", "courses"), result.todos.map { it.id })
        assertEquals(result.todos, result.all)
        assertFalse(TodoFilter().isActive)
    }

    @Test
    fun querySearchesTitleAndNoteWithoutCaseOrAccents() = scope.runTest {
        assertEquals(listOf("tache"), ids(TodoFilter(query = "TACHE")))
        assertEquals(listOf("courses"), ids(TodoFilter(query = "creme oeufs")))
        assertEquals(emptyList(), ids(TodoFilter(query = "creme dentiste")))
        assertTrue(TodoFilter(query = "creme").isActive)
        assertFalse(TodoFilter(query = "   ").isActive)
    }

    @Test
    fun statusFilterSplitsPendingAndDone() = scope.runTest {
        assertEquals(listOf("dentiste", "facture", "tache"), ids(TodoFilter(status = TodoStatusFilter.Pending)))
        assertEquals(listOf("courses"), ids(TodoFilter(status = TodoStatusFilter.Done)))
    }

    @Test
    fun reminderAndAttachmentFiltersAreCumulative() = scope.runTest {
        assertEquals(listOf("dentiste"), ids(TodoFilter(withReminder = true)))
        assertEquals(listOf("facture"), ids(TodoFilter(withAttachments = true)))
        assertEquals(emptyList(), ids(TodoFilter(withReminder = true, withAttachments = true)))
    }

    @Test
    fun allTodosStayAvailableWhenNothingMatches() = scope.runTest {
        val result = filtered(TodoFilter(query = "introuvable"))

        assertEquals(emptyList(), result.todos)
        assertEquals(4, result.all.size)
    }
}

/** Liste figée : seule l'observation sert au filtrage. */
private class FixedTodoRepository(private val todos: List<Todo>) : TodoRepository {
    override fun observeTodos(): Flow<List<Todo>> = flowOf(todos)
    override suspend fun addTodo(title: String): AppResult<Todo> = error("non utilisé")
    override suspend fun toggleTodo(id: String): AppResult<Todo> = error("non utilisé")
    override suspend fun deleteTodo(id: String): AppResult<Unit> = error("non utilisé")
    override suspend fun setReminder(id: String, at: Long?): AppResult<Todo> = error("non utilisé")
    override suspend fun setNote(id: String, note: String): AppResult<Todo> = error("non utilisé")
    override fun observeAttachments(todoId: String): Flow<List<TodoAttachment>> = error("non utilisé")
    override suspend fun addAttachment(
        todoId: String,
        fileName: String,
        type: AttachmentType,
        bytes: ByteArray,
    ): AppResult<TodoAttachment> = error("non utilisé")
    override suspend fun deleteAttachment(id: String): AppResult<Unit> = error("non utilisé")
}
