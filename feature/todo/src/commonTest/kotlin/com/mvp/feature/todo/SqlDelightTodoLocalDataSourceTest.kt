package com.mvp.feature.todo

import com.mvp.core.database.testing.createInMemoryDriver
import com.mvp.feature.todo.data.db.TodoDatabase
import com.mvp.feature.todo.data.source.SqlDelightTodoLocalDataSource
import com.mvp.feature.todo.domain.model.Todo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightTodoLocalDataSourceTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val driver = createInMemoryDriver(TodoDatabase.Schema)

    /** Une nouvelle instance sur la même base équivaut à un redémarrage de l'app. */
    private fun newDataSource() = SqlDelightTodoLocalDataSource(TodoDatabase(driver), dispatcher)

    @AfterTest
    fun closeDriver() = driver.close()

    @Test
    fun todosSurviveRestart() = scope.runTest {
        newDataSource().apply {
            upsert(Todo("a", "Acheter du pain", isDone = false))
            upsert(Todo("b", "Appeler Paul", isDone = true))
        }

        val restarted = newDataSource()

        assertEquals(
            listOf(Todo("a", "Acheter du pain", isDone = false), Todo("b", "Appeler Paul", isDone = true)),
            restarted.observe().first(),
        )
    }

    @Test
    fun upsertUpdatesExistingTodoAndKeepsItsPosition() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Première", isDone = false))
        source.upsert(Todo("b", "Deuxième", isDone = false))

        source.upsert(Todo("a", "Première", isDone = true))

        assertEquals(listOf("a", "b"), source.observe().first().map { it.id })
        assertEquals(Todo("a", "Première", isDone = true), source.get("a"))
    }

    @Test
    fun deleteRemovesTodoAndReportsWhetherItExisted() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "À supprimer", isDone = false))

        assertTrue(source.delete("a"))
        assertNull(source.get("a"))
        assertFalse(source.delete("a"))
        assertEquals(emptyList(), newDataSource().observe().first())
    }

    @Test
    fun observeEmitsAgainAfterChange() = scope.runTest {
        val source = newDataSource()
        val emissions = mutableListOf<List<Todo>>()
        val job = launch { source.observe().collect { emissions += it } }
        testScheduler.advanceUntilIdle()

        source.upsert(Todo("a", "Nouvelle", isDone = false))
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(emptyList(), listOf(Todo("a", "Nouvelle", isDone = false))), emissions)
    }
}
