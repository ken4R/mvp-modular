package com.mvp.feature.todo

import com.mvp.core.common.domain.analytics.AnalyticsEvent
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.todo.data.repository.TodoRepositoryImpl
import com.mvp.feature.todo.data.source.InMemoryTodoLocalDataSource
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.CompleteTodoFromWidgetUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompleteTodoFromWidgetUseCaseTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private var nextId = 0
    private var now = 1_000_000L
    private val repository = TodoRepositoryImpl(InMemoryTodoLocalDataSource(), idGenerator = { "id-${nextId++}" })
    private val session = FakeSessionExpiry(expiresAt = now + DAY)
    private val appLock = FakeAppLockPreference(enabled = false)
    private val analytics = FakeAnalyticsTracker()

    private val add = AddTodoUseCase(repository, FakeAnalyticsTracker(), dispatcher)
    private val delete = DeleteTodoUseCase(repository, dispatcher)
    private val complete = CompleteTodoFromWidgetUseCase(repository, session, appLock, analytics, { now }, dispatcher)

    private suspend fun isDone(id: String) = repository.observeTodos().first().single { it.id == id }.isDone

    @Test
    fun completesTheTodoAndMeasuresIt() = scope.runTest {
        add("Appeler Paul")

        assertEquals(AppResult.Success(Unit), complete("id-0"))

        assertTrue(isDone("id-0"))
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.TaskCompleted), analytics.events)
    }

    @Test
    fun aRepeatedRequestNeverReopensTheTodo() = scope.runTest {
        add("Appeler Paul")
        complete("id-0")

        assertEquals(AppResult.Success(Unit), complete("id-0"))

        assertTrue(isDone("id-0"))
        assertEquals(1, analytics.events.size)
    }

    @Test
    fun twoTapsAtTheSameTimeDoNotReopenTheTodo() = scope.runTest {
        add("Appeler Paul")
        val other = CompleteTodoFromWidgetUseCase(repository, session, appLock, analytics, { now }, dispatcher)

        listOf(async { complete("id-0") }, async { other("id-0") }).awaitAll()

        assertTrue(isDone("id-0"))
        assertEquals(1, analytics.events.size)
    }

    @Test
    fun nothingChangesWithoutSession() = scope.runTest {
        add("Appeler Paul")
        session.expiresAt.value = null

        assertEquals(AppResult.Failure(AppError.Unauthorized), complete("id-0"))
        assertEquals(false, isDone("id-0"))
    }

    @Test
    fun nothingChangesOnceTheSessionHasExpired() = scope.runTest {
        add("Appeler Paul")
        now += DAY

        assertEquals(AppResult.Failure(AppError.Unauthorized), complete("id-0"))
        assertEquals(false, isDone("id-0"))
    }

    @Test
    fun nothingChangesWhileTheAppLockIsOn() = scope.runTest {
        add("Appeler Paul")
        appLock.enabled.value = true

        assertEquals(AppResult.Failure(AppError.PermissionDenied), complete("id-0"))
        assertEquals(false, isDone("id-0"))
    }

    @Test
    fun aTodoInTheTrashIsNotFound() = scope.runTest {
        add("Appeler Paul")
        delete("id-0")

        assertEquals(AppResult.Failure(AppError.NotFound), complete("id-0"))
        assertEquals(AppResult.Failure(AppError.NotFound), complete("inconnue"))
        assertTrue(analytics.events.isEmpty())
    }

    private companion object {
        const val MINUTE = 60 * 1000L
        const val DAY = 24 * 60 * MINUTE
    }
}
