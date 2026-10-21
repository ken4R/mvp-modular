package com.mvp.feature.todo

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.database.testing.createInMemoryDriver
import com.mvp.feature.todo.data.db.TodoDatabase
import com.mvp.feature.todo.data.session.TodoUserSessionObserver
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
    fun toggleFlipsStateAndNeverRecreatesDeletedTodo() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Tâche", isDone = false))

        assertEquals(Todo("a", "Tâche", isDone = true), source.toggle("a"))
        assertEquals(Todo("a", "Tâche", isDone = true), newDataSource().get("a"))

        source.delete("a")
        assertNull(source.toggle("a"))
        assertNull(source.get("a"))
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

    @Test
    fun reminderSurvivesRestartAndIsKeptByToggle() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Appeler Paul", isDone = false))

        assertEquals(Todo("a", "Appeler Paul", isDone = false, reminderAt = 42L), source.setReminder("a", 42L))
        source.toggle("a")

        assertEquals(Todo("a", "Appeler Paul", isDone = true, reminderAt = 42L), newDataSource().get("a"))
        assertEquals(Todo("a", "Appeler Paul", isDone = true), source.setReminder("a", null))
        assertNull(source.setReminder("inconnue", 42L))
        assertNull(source.get("inconnue"))
    }

    @Test
    fun logoutDeletesAllTodosAndCancelsReminders() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Acheter du pain", isDone = false, reminderAt = 42L))
        source.upsert(Todo("b", "Appeler Paul", isDone = true))
        val reminders = FakeReminderScheduler()

        assertEquals(AppResult.Success(Unit), TodoUserSessionObserver(source, reminders).clearUserData())

        assertEquals(emptyList(), newDataSource().observe().first())
        assertEquals(1, reminders.cancelAllCalls)
    }

    @Test
    fun migrationFromVersion1KeepsTodosWithoutReminder() = scope.runTest {
        val oldDriver = createInMemoryDriver(SchemaV1)
        try {
            oldDriver.execute(null, "INSERT INTO TodoEntity(id, title, is_done) VALUES ('a', 'Ancienne', 1)", 0)

            TodoDatabase.Schema.migrate(oldDriver, oldVersion = 1, newVersion = TodoDatabase.Schema.version)
            val source = SqlDelightTodoLocalDataSource(TodoDatabase(oldDriver), dispatcher)

            assertEquals(listOf(Todo("a", "Ancienne", isDone = true)), source.observe().first())
            assertEquals(Todo("a", "Ancienne", isDone = true, reminderAt = 42L), source.setReminder("a", 42L))
        } finally {
            oldDriver.close()
        }
    }
}

/** Schéma de `todo.db` avant les rappels (version 1), pour tester la migration. */
private object SchemaV1 : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
        driver.execute(
            null,
            "CREATE TABLE TodoEntity (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, is_done INTEGER NOT NULL DEFAULT 0)",
            0,
        )
        return QueryResult.Unit
    }

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> = QueryResult.Unit
}
