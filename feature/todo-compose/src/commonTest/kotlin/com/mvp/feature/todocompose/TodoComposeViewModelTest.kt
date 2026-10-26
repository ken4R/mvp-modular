package com.mvp.feature.todocompose

import com.mvp.core.common.domain.analytics.AnalyticsEvent
import com.mvp.core.common.domain.analytics.AnalyticsTracker
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.notification.NotificationsPreference
import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.LocationReminder
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoAttachment
import com.mvp.feature.todo.domain.repository.TodoRepository
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveRemindersEnabledUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodosUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import com.mvp.feature.todocompose.ui.TodoComposeUiState
import com.mvp.feature.todocompose.ui.TodoComposeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Le ViewModel partagé de l'écran Compose, branché sur les vrais use cases de feature:todo. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodoComposeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = InMemoryTodoRepository()
    private val notifications = FakeNotificationsPreference(enabled = true)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TodoComposeViewModel(
        observeTodos = ObserveTodosUseCase(repository, dispatcher),
        observeRemindersEnabled = ObserveRemindersEnabledUseCase(notifications, dispatcher),
        addTodo = AddTodoUseCase(repository, NoOpAnalyticsTracker, dispatcher),
        toggleTodo = ToggleTodoUseCase(repository, NoOpAnalyticsTracker, dispatcher),
        deleteTodo = DeleteTodoUseCase(repository, dispatcher),
    )

    @Test
    fun addsTodoAndClearsInput() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onInputChange("  Acheter du pain ")
        viewModel.onAdd()

        val state = viewModel.state.value
        assertEquals(listOf("Acheter du pain"), state.todos.map { it.title })
        assertEquals("", state.input)
        assertNull(state.error)
    }

    @Test
    fun blankTitleShowsValidationErrorUntilInputChanges() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onInputChange("   ")
        viewModel.onAdd()
        assertEquals("Le titre est obligatoire", viewModel.state.value.error)
        assertTrue(viewModel.state.value.todos.isEmpty())

        viewModel.onInputChange("Préparer la démo")
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun titleOverEightyCharactersIsRefused() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onInputChange("a".repeat(81))
        viewModel.onAdd()

        assertEquals("80 caractères maximum", viewModel.state.value.error)
        assertTrue(viewModel.state.value.todos.isEmpty())
    }

    @Test
    fun toggledTodoMovesAfterOpenOnesAndDeleteRemovesIt() = runTest(dispatcher) {
        val viewModel = viewModel()
        listOf("Un", "Deux").forEach {
            viewModel.onInputChange(it)
            viewModel.onAdd()
        }
        val first = viewModel.state.value.todos.first { it.title == "Un" }

        viewModel.onToggle(first.id)
        assertEquals(listOf("Deux", "Un"), viewModel.state.value.todos.map { it.title })
        assertTrue(viewModel.state.value.todos.last().isDone)

        viewModel.onDelete(first.id)
        assertEquals(listOf("Deux"), viewModel.state.value.todos.map { it.title })
    }

    @Test
    fun unknownTodoShowsNotFound() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onToggle("absente")

        assertEquals("Tâche introuvable", viewModel.state.value.error)
    }

    @Test
    fun followsNotificationsSetting() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.remindersEnabled)

        notifications.enabled.value = false

        assertFalse(viewModel.state.value.remindersEnabled)
    }

    @Test
    fun remindersHintOnlyForUpcomingRemindersOfOpenTodosWhenNotificationsAreOff() {
        val now = 1_000L
        val upcoming = Todo(id = "1", title = "A", isDone = false, reminderAt = now + 1)
        val off = TodoComposeUiState(todos = listOf(upcoming), remindersEnabled = false)

        assertTrue(off.showRemindersDisabledHint(now))
        assertFalse(off.copy(remindersEnabled = true).showRemindersDisabledHint(now))
        assertFalse(off.copy(todos = listOf(upcoming.copy(isDone = true))).showRemindersDisabledHint(now))
        assertFalse(off.copy(todos = listOf(upcoming.copy(reminderAt = now - 1))).showRemindersDisabledHint(now))
        assertFalse(off.copy(todos = listOf(upcoming.copy(reminderAt = null))).showRemindersDisabledHint(now))
    }
}

private class InMemoryTodoRepository : TodoRepository {
    private val todos = MutableStateFlow<List<Todo>>(emptyList())
    private var nextId = 0

    override fun observeTodos(): Flow<List<Todo>> = todos

    override suspend fun addTodo(title: String): AppResult<Todo> {
        val todo = Todo(id = "${nextId++}", title = title, isDone = false)
        todos.update { it + todo }
        return AppResult.Success(todo)
    }

    override suspend fun toggleTodo(id: String): AppResult<Todo> = update(id) { it.copy(isDone = !it.isDone) }

    override suspend fun deleteTodo(id: String): AppResult<Unit> {
        if (todos.value.none { it.id == id }) return AppResult.Failure(AppError.NotFound)
        todos.update { list -> list.filterNot { it.id == id } }
        return AppResult.Success(Unit)
    }

    override suspend fun setReminder(id: String, at: Long?): AppResult<Todo> = update(id) { it.copy(reminderAt = at) }

    override suspend fun setNote(id: String, note: String): AppResult<Todo> = update(id) { it.copy(note = note) }

    override suspend fun setDueDate(id: String, dueDate: Int?): AppResult<Todo> = update(id) { it.copy(dueDate = dueDate) }
    override suspend fun setLocationReminder(id: String, reminder: LocationReminder?): AppResult<Todo> =
        update(id) { it.copy(locationReminder = reminder) }

    // Pièces jointes : absentes de l'écran Compose.
    override fun observeAttachments(todoId: String): Flow<List<TodoAttachment>> = flowOf(emptyList())

    override suspend fun addAttachment(
        todoId: String,
        fileName: String,
        type: AttachmentType,
        bytes: ByteArray,
    ): AppResult<TodoAttachment> = AppResult.Failure(AppError.Unavailable)

    override suspend fun deleteAttachment(id: String): AppResult<Unit> = AppResult.Failure(AppError.NotFound)

    private fun update(id: String, change: (Todo) -> Todo): AppResult<Todo> {
        val todo = todos.value.firstOrNull { it.id == id } ?: return AppResult.Failure(AppError.NotFound)
        val updated = change(todo)
        todos.update { list -> list.map { if (it.id == id) updated else it } }
        return AppResult.Success(updated)
    }
}

private class FakeNotificationsPreference(enabled: Boolean) : NotificationsPreference {
    val enabled = MutableStateFlow(enabled)
    override fun observeNotificationsEnabled(): Flow<Boolean> = enabled
}

private object NoOpAnalyticsTracker : AnalyticsTracker {
    override suspend fun track(event: AnalyticsEvent) = Unit
}
