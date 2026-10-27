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
import com.mvp.feature.todo.data.sync.FakeTodoRemoteDataSource
import com.mvp.feature.todo.data.sync.TodoRecord
import com.mvp.feature.todo.data.sync.TodoSyncRepositoryImpl
import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.LocationReminder
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoAttachment
import com.mvp.feature.todo.domain.model.TodoPriority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightTodoLocalDataSourceTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val driver = createInMemoryDriver(TodoDatabase.Schema)

    /** Partagés par les instances : comme le dossier de l'app, ils survivent au redémarrage. */
    private val files = FakeFileStore()

    /** Une nouvelle instance sur la même base équivaut à un redémarrage de l'app. */
    private fun newDataSource() = SqlDelightTodoLocalDataSource(TodoDatabase(driver), dispatcher, files, now = { clock })

    /** Heure des modifications (epoch ms), avancée par les tests. */
    private var clock = 1_000L

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
    fun locationReminderSurvivesRestartAndStaysOnTheDevice() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Appeler Paul", isDone = false))
        source.markSent(source.pendingChanges())
        clock = 2_000L

        assertEquals(office, source.setLocationReminder("a", office)?.locationReminder)

        assertEquals(office, newDataSource().get("a")?.locationReminder)
        // Propre à l'appareil, comme le rappel à l'heure : rien ne part au serveur.
        assertEquals(emptyList(), source.pendingChanges())
        assertNull(source.setLocationReminder("a", null)?.locationReminder)
        assertNull(newDataSource().get("a")?.locationReminder)
        assertNull(source.setLocationReminder("inconnue", office))
    }

    @Test
    fun applyRemoteKeepsTheLocalLocationReminder() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Appeler Paul", isDone = false, locationReminder = office))
        source.markSent(source.pendingChanges())

        source.applyRemote(listOf(TodoRecord("a", "Appeler Paul et Marie", isDone = false, updatedAt = 5_000L)))

        assertEquals(Todo("a", "Appeler Paul et Marie", isDone = false, locationReminder = office), source.get("a"))
    }

    @Test
    fun logoutDeletesAllTodosAndCancelsReminders() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Acheter du pain", isDone = false, reminderAt = 42L))
        source.upsert(Todo("b", "Appeler Paul", isDone = true))
        source.upsert(Todo("c", "Au bureau", isDone = false, locationReminder = office))
        val reminders = FakeReminderScheduler()
        val locationReminders = FakeLocationReminderScheduler()

        val sync = TodoSyncRepositoryImpl(source, FakeTodoRemoteDataSource(FakeConnectivityObserver(online = true), latencyMillis = 0))

        assertEquals(AppResult.Success(Unit), TodoUserSessionObserver(source, reminders, locationReminders, sync).clearUserData())

        // Les lieux des rappels par lieu partent avec les tâches (ADR 0023).
        assertEquals(emptyList(), newDataSource().observe().first())
        assertEquals(emptyList(), newDataSource().pendingChanges())
        assertEquals(1, reminders.cancelAllCalls)
        assertEquals(1, locationReminders.cancelAllCalls)
    }

    @Test
    fun everySyncedChangeEntersTheOutboxButNotReminders() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Acheter du pain", isDone = false))
        source.upsert(Todo("b", "Appeler Paul", isDone = false))
        clock = 2_000L
        source.toggle("a")
        source.delete("b")
        source.setReminder("a", 42L)

        assertEquals(
            listOf(
                TodoRecord("a", "Acheter du pain", isDone = true, updatedAt = 2_000L),
                TodoRecord("b", "", isDone = false, updatedAt = 2_000L, isDeleted = true),
            ).sortedBy { it.id },
            newDataSource().pendingChanges().sortedBy { it.id },
        )
        assertEquals(2, source.observePendingCount().first())
    }

    @Test
    fun changesKeepIncreasingTimesEvenWhenTheClockGoesBack() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Tâche", isDone = false))
        clock = 500L
        source.toggle("a")

        assertEquals(1_001L, source.pendingChanges().single().updatedAt)
    }

    @Test
    fun markSentKeepsATodoChangedAgainDuringTheUpload() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Première", isDone = false))
        source.upsert(Todo("b", "Deuxième", isDone = false))
        val sent = source.pendingChanges()
        clock = 5_000L
        source.toggle("a")

        source.markSent(sent)

        assertEquals(listOf(TodoRecord("a", "Première", isDone = true, updatedAt = 5_000L)), source.pendingChanges())
    }

    @Test
    fun applyRemoteKeepsTheNewestVersionAndTheLocalReminder() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("old", "Ancienne ici", isDone = false))
        source.upsert(Todo("newer", "Plus récente ici", isDone = false, reminderAt = 42L))
        source.upsert(Todo("gone", "Supprimée ailleurs", isDone = false))
        source.markSent(source.pendingChanges())

        source.applyRemote(
            listOf(
                TodoRecord("old", "Ancienne ici", isDone = false, updatedAt = 500L),
                TodoRecord("newer", "Modifiée ailleurs", isDone = true, updatedAt = 3_000L),
                TodoRecord("gone", "", isDone = false, updatedAt = 3_000L, isDeleted = true),
                TodoRecord("other", "Créée ailleurs", isDone = false, updatedAt = 3_000L),
                TodoRecord("ghost", "", isDone = false, updatedAt = 3_000L, isDeleted = true),
                // Versions que l'app n'aurait pas pu écrire : ignorées.
                TodoRecord("blank", "  ", isDone = false, updatedAt = 3_000L),
                TodoRecord("long", "x".repeat(81), isDone = false, updatedAt = 3_000L),
            ),
        )

        assertEquals(
            listOf(
                Todo("old", "Ancienne ici", isDone = false),
                Todo("newer", "Modifiée ailleurs", isDone = true, reminderAt = 42L),
                Todo("other", "Créée ailleurs", isDone = false),
            ),
            newDataSource().observe().first(),
        )
        // Ce qui vient du serveur n'y repart pas.
        assertEquals(emptyList(), source.pendingChanges())
    }

    @Test
    fun applyRemoteNeverOverwritesAPendingLocalChange() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Modifiée hors ligne", isDone = false))
        source.delete("a")
        source.upsert(Todo("b", "En attente", isDone = false))

        source.applyRemote(
            listOf(
                TodoRecord("a", "Version du serveur", isDone = true, updatedAt = 9_000L),
                TodoRecord("b", "Version du serveur", isDone = true, updatedAt = 9_000L),
            ),
        )

        assertEquals(listOf(Todo("b", "En attente", isDone = false)), source.observe().first())
        assertEquals(2, source.pendingChanges().size)
    }

    @Test
    fun noteSurvivesRestartAndEntersTheOutbox() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Acheter du pain", isDone = false))
        source.markSent(source.pendingChanges())
        clock = 2_000L

        assertEquals(Todo("a", "Acheter du pain", isDone = false, note = "Complet"), source.setNote("a", "Complet"))

        assertEquals(Todo("a", "Acheter du pain", isDone = false, note = "Complet"), newDataSource().get("a"))
        assertEquals(
            listOf(TodoRecord("a", "Acheter du pain", isDone = false, updatedAt = 2_000L, note = "Complet")),
            source.pendingChanges(),
        )
        assertNull(source.setNote("inconnue", "Note"))
    }

    @Test
    fun dueDateSurvivesRestartAndEntersTheOutbox() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Acheter du pain", isDone = false))
        source.markSent(source.pendingChanges())
        clock = 2_000L

        assertEquals(Todo("a", "Acheter du pain", isDone = false, dueDate = 20_754), source.setDueDate("a", 20_754))

        assertEquals(Todo("a", "Acheter du pain", isDone = false, dueDate = 20_754), newDataSource().get("a"))
        assertEquals(
            listOf(TodoRecord("a", "Acheter du pain", isDone = false, updatedAt = 2_000L, dueDate = 20_754)),
            source.pendingChanges(),
        )

        // Retirée : la tâche repart au serveur sans échéance ; la même échéance ne change rien.
        source.markSent(source.pendingChanges())
        clock = 3_000L
        source.setDueDate("a", null)
        assertEquals(listOf(TodoRecord("a", "Acheter du pain", isDone = false, updatedAt = 3_000L)), source.pendingChanges())
        source.markSent(source.pendingChanges())
        source.setDueDate("a", null)
        assertEquals(emptyList(), source.pendingChanges())
        assertNull(source.setDueDate("inconnue", 20_754))
    }

    @Test
    fun applyRemoteBringsTheDueDateAndIgnoresAnOutOfRangeOne() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Tâche", isDone = false, reminderAt = 42L))
        source.markSent(source.pendingChanges())

        source.applyRemote(
            listOf(
                TodoRecord("a", "Tâche", isDone = false, updatedAt = 3_000L, dueDate = 20_754),
                TodoRecord("b", "Créée ailleurs", isDone = false, updatedAt = 3_000L, dueDate = 20_760),
                TodoRecord("far", "Tâche", isDone = false, updatedAt = 3_000L, dueDate = 1_000_000),
                // Un id qui ajouterait une adresse au lien de la tâche (partage, calendrier).
                TodoRecord("x https://piege.example", "Tâche", isDone = false, updatedAt = 3_000L, dueDate = 20_760),
            ),
        )

        assertEquals(
            listOf(
                Todo("a", "Tâche", isDone = false, reminderAt = 42L, dueDate = 20_754),
                Todo("b", "Créée ailleurs", isDone = false, dueDate = 20_760),
            ),
            source.observe().first(),
        )
    }

    @Test
    fun attachmentIsStoredWithItsFileCountedAndKeptOutOfTheOutbox() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Tâche", isDone = false))
        source.markSent(source.pendingChanges())
        clock = 3_000L

        val added = source.addAttachment("p1", "a", "Photo.jpg", AttachmentType.Photo, byteArrayOf(1, 2, 3))

        val expected = TodoAttachment("p1", "a", "Photo.jpg", AttachmentType.Photo, sizeBytes = 3, addedAt = 3_000L, localPath = "/fake/p1")
        assertEquals(expected, added)
        assertEquals(listOf(expected), newDataSource().observeAttachments("a").first())
        assertContentEquals(byteArrayOf(1, 2, 3), files.read("p1"))
        assertEquals(1, newDataSource().get("a")?.attachmentCount)
        assertEquals(listOf(1), newDataSource().observe().first().map { it.attachmentCount })
        assertEquals(emptyList(), source.pendingChanges())
    }

    @Test
    fun taskListEmitsAgainWhenAnAttachmentIsAddedOrRemoved() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Tâche", isDone = false))
        val counts = mutableListOf<Int>()
        val job = launch { source.observe().collect { todos -> counts += todos.single().attachmentCount } }
        testScheduler.advanceUntilIdle()

        source.addAttachment("p1", "a", "Photo.jpg", AttachmentType.Photo, byteArrayOf(1))
        testScheduler.advanceUntilIdle()
        source.deleteAttachment("p1")
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(0, 1, 0), counts)
    }

    @Test
    fun attachmentForAMissingTaskOrAFailedWriteKeepsNothing() = scope.runTest {
        val source = newDataSource()

        assertNull(source.addAttachment("p1", "inconnue", "Photo.jpg", AttachmentType.Photo, byteArrayOf(1)))
        assertEquals(emptyList(), files.names())

        source.upsert(Todo("a", "Tâche", isDone = false))
        files.failure = IllegalStateException("Stockage plein")
        assertFailsWith<IllegalStateException> {
            source.addAttachment("p2", "a", "Photo.jpg", AttachmentType.Photo, byteArrayOf(1))
        }
        assertEquals(emptyList(), source.observeAttachments("a").first())
        assertEquals(0, source.get("a")?.attachmentCount)
    }

    @Test
    fun deletingAnAttachmentOrItsTaskDeletesTheFiles() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Tâche", isDone = false))
        source.upsert(Todo("b", "Autre", isDone = false))
        source.addAttachment("p1", "a", "Photo.jpg", AttachmentType.Photo, byteArrayOf(1))
        source.addAttachment("p2", "a", "Plan.pdf", AttachmentType.Pdf, byteArrayOf(2))
        source.addAttachment("p3", "b", "Liste.txt", AttachmentType.Text, byteArrayOf(3))

        assertTrue(source.deleteAttachment("p1"))
        assertFalse(source.deleteAttachment("p1"))
        assertEquals(listOf("p2", "p3"), files.names().sorted())

        source.delete("a")
        assertEquals(listOf("p3"), files.names())
        assertEquals(emptyList(), source.observeAttachments("a").first())
        assertEquals(1, source.observeAttachments("b").first().size)
    }

    @Test
    fun logoutDeletesAttachmentsAndTheirFiles() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Tâche", isDone = false, note = "Personnel"))
        source.addAttachment("p1", "a", "Photo.jpg", AttachmentType.Photo, byteArrayOf(1))
        val sync = TodoSyncRepositoryImpl(source, FakeTodoRemoteDataSource(FakeConnectivityObserver(online = true), latencyMillis = 0))

        TodoUserSessionObserver(source, FakeReminderScheduler(), FakeLocationReminderScheduler(), sync).clearUserData()

        assertEquals(emptyList(), newDataSource().observeAttachments("a").first())
        assertEquals(emptyList(), files.names())
    }

    @Test
    fun logoutFailsIfTheFilesCannotBeErased() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Tâche", isDone = false))
        source.addAttachment("p1", "a", "Photo.jpg", AttachmentType.Photo, byteArrayOf(1))
        val sync = TodoSyncRepositoryImpl(source, FakeTodoRemoteDataSource(FakeConnectivityObserver(online = true), latencyMillis = 0))
        files.failure = IllegalStateException("Dossier impossible à effacer")

        val result = TodoUserSessionObserver(source, FakeReminderScheduler(), FakeLocationReminderScheduler(), sync).clearUserData()

        // La déconnexion n'a pas lieu : la session est gardée et l'effacement pourra être retenté.
        assertIs<AppResult.Failure>(result)
        files.failure = null
        assertEquals(AppResult.Success(Unit), TodoUserSessionObserver(source, FakeReminderScheduler(), FakeLocationReminderScheduler(), sync).clearUserData())
        assertEquals(emptyList(), files.names())
    }

    @Test
    fun applyRemoteBringsTheNoteAndIgnoresATooLongOne() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Tâche", isDone = false, reminderAt = 42L))
        source.addAttachment("p1", "a", "Photo.jpg", AttachmentType.Photo, byteArrayOf(1))
        source.markSent(source.pendingChanges())

        source.applyRemote(
            listOf(
                TodoRecord("a", "Tâche", isDone = false, updatedAt = 3_000L, note = "Écrite ailleurs"),
                TodoRecord("long", "Tâche", isDone = false, updatedAt = 3_000L, note = "x".repeat(2_001)),
            ),
        )

        assertEquals(
            listOf(Todo("a", "Tâche", isDone = false, reminderAt = 42L, note = "Écrite ailleurs", attachmentCount = 1)),
            source.observe().first(),
        )
    }

    @Test
    fun completionTimeIsSetWhenDoneKeptByOtherChangesAndClearedWhenUndone() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Tâche", isDone = false))
        source.upsert(Todo("b", "Déjà faite", isDone = true))
        assertEquals(listOf(1_000L), source.observeCompletionTimes().first())

        clock = 2_000L
        source.toggle("a")
        clock = 3_000L
        // Rappel, titre ou note d'une tâche déjà terminée : l'heure de fin ne bouge pas.
        source.setReminder("a", 42L)
        source.setNote("a", "Note")
        assertEquals(listOf(1_000L, 2_000L), newDataSource().observeCompletionTimes().first().sorted())

        // Rouverte puis terminée de nouveau : la nouvelle heure ; supprimée : elle ne compte plus.
        source.toggle("a")
        assertEquals(listOf(1_000L), source.observeCompletionTimes().first())
        clock = 4_000L
        source.toggle("a")
        source.delete("b")
        assertEquals(listOf(4_000L), source.observeCompletionTimes().first())
    }

    @Test
    fun applyRemoteDatesATodoDoneElsewhereAndKeepsTheLocalCompletionTime() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Faite ici", isDone = true))
        source.upsert(Todo("b", "À faire", isDone = false))
        source.markSent(source.pendingChanges())

        source.applyRemote(
            listOf(
                TodoRecord("a", "Renommée ailleurs", isDone = true, updatedAt = 5_000L),
                TodoRecord("b", "À faire", isDone = true, updatedAt = 6_000L),
                TodoRecord("c", "Créée faite ailleurs", isDone = true, updatedAt = 7_000L),
                TodoRecord("d", "Créée à faire ailleurs", isDone = false, updatedAt = 7_000L),
            ),
        )
        assertEquals(listOf(1_000L, 6_000L, 7_000L), source.observeCompletionTimes().first().sorted())

        source.applyRemote(listOf(TodoRecord("c", "Rouverte ailleurs", isDone = false, updatedAt = 8_000L)))
        assertEquals(listOf(1_000L, 6_000L), source.observeCompletionTimes().first().sorted())
    }

    @Test
    fun migrationToVersion5DatesDoneTodosByTheirLastChange() = scope.runTest {
        val oldDriver = createInMemoryDriver(SchemaV1)
        try {
            TodoDatabase.Schema.migrate(oldDriver, oldVersion = 1, newVersion = 4)
            oldDriver.execute(
                null,
                "INSERT INTO TodoEntity(id, title, is_done, updated_at) VALUES " +
                    "('done', 'Faite', 1, 900), ('old', 'Faite avant la synchronisation', 1, 0), ('todo', 'À faire', 0, 800)",
                0,
            )

            TodoDatabase.Schema.migrate(oldDriver, oldVersion = 4, newVersion = TodoDatabase.Schema.version)
            val source = SqlDelightTodoLocalDataSource(TodoDatabase(oldDriver), dispatcher, FakeFileStore())

            assertEquals(listOf(900L), source.observeCompletionTimes().first())
        } finally {
            oldDriver.close()
        }
    }

    @Test
    fun migrationFromVersion1KeepsTodosWithoutReminder() = scope.runTest {
        val oldDriver = createInMemoryDriver(SchemaV1)
        try {
            oldDriver.execute(null, "INSERT INTO TodoEntity(id, title, is_done) VALUES ('a', 'Ancienne', 1)", 0)

            TodoDatabase.Schema.migrate(oldDriver, oldVersion = 1, newVersion = TodoDatabase.Schema.version)
            val source = SqlDelightTodoLocalDataSource(TodoDatabase(oldDriver), dispatcher, FakeFileStore())

            // Version 8 : clé d'ordre tirée du rowid, pas de priorité.
            val migrated = Todo("a", "Ancienne", isDone = true, position = "h00000001")
            assertEquals(listOf(migrated), source.observe().first())
            assertEquals(migrated.copy(reminderAt = 42L), source.setReminder("a", 42L))
            // Version 3 : les tâches d'avant la synchronisation partent au premier envoi, comme les plus anciennes.
            assertEquals(
                listOf(TodoRecord("a", "Ancienne", isDone = true, updatedAt = 0, position = "h00000001")),
                source.pendingChanges(),
            )
            // Version 4 : note vide, et la table des pièces jointes existe.
            assertEquals("", source.get("a")?.note)
            assertEquals("Photo.jpg", source.addAttachment("p1", "a", "Photo.jpg", AttachmentType.Photo, byteArrayOf(1))?.fileName)
            assertEquals(1, source.get("a")?.attachmentCount)
            // Version 5 : terminée avant la synchronisation, sans heure de fin, elle ne compte pas.
            assertEquals(emptyList(), source.observeCompletionTimes().first())
            // Version 6 : pas d'échéance, et elle peut en recevoir une.
            assertNull(source.get("a")?.dueDate)
            assertEquals(20_754, source.setDueDate("a", 20_754)?.dueDate)
            // Version 7 : pas de rappel par lieu, et elle peut en recevoir un.
            assertNull(source.get("a")?.locationReminder)
            assertEquals(office, source.setLocationReminder("a", office)?.locationReminder)
            assertEquals(TodoPriority.High, source.setPriority("a", TodoPriority.High)?.priority)
        } finally {
            oldDriver.close()
        }
    }

    @Test
    fun migrationToVersion8KeepsTheCreationOrder() = scope.runTest {
        val oldDriver = createInMemoryDriver(SchemaV1)
        try {
            TodoDatabase.Schema.migrate(oldDriver, oldVersion = 1, newVersion = 7)
            oldDriver.execute(
                null,
                "INSERT INTO TodoEntity(id, title, is_done) VALUES ('z', 'Première', 0), ('m', 'Deuxième', 0), ('a', 'Troisième', 0)",
                0,
            )

            TodoDatabase.Schema.migrate(oldDriver, oldVersion = 7, newVersion = TodoDatabase.Schema.version)
            val source = SqlDelightTodoLocalDataSource(TodoDatabase(oldDriver), dispatcher, FakeFileStore())

            val todos = source.observe().first()
            assertEquals(listOf("z", "m", "a"), todos.map { it.id })
            assertEquals(listOf("h00000001", "h00000002", "h00000003"), todos.map { it.position })
            assertTrue(todos.all { it.priority == TodoPriority.None })
            assertEquals("h00000003", source.lastPosition())
        } finally {
            oldDriver.close()
        }
    }

    @Test
    fun todosAreListedByPositionThenCreationOrder() = scope.runTest {
        val source = newDataSource()
        assertNull(source.lastPosition())
        source.upsert(Todo("a", "Clé haute", isDone = false, position = "a5"))
        source.upsert(Todo("b", "Clé basse", isDone = false, position = "a1"))
        source.upsert(Todo("d", "Même clé, ajoutée avant", isDone = false, position = "a3"))
        source.upsert(Todo("c", "Même clé, ajoutée après", isDone = false, position = "a3"))

        assertEquals(listOf("b", "d", "c", "a"), newDataSource().observe().first().map { it.id })
        assertEquals("a5", source.lastPosition())
    }

    @Test
    fun priorityAndPositionAreSavedAndSent() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Tâche", isDone = false, position = "a0"))
        source.upsert(Todo("b", "Autre", isDone = false, position = "a1"))
        source.markSent(source.pendingChanges())

        clock = 2_000L
        source.setPriority("a", TodoPriority.Medium)
        clock = 3_000L
        source.setPositions(mapOf("b" to "Zz", "supprimée" to "a5"))

        assertEquals(listOf("b", "a"), newDataSource().observe().first().map { it.id })
        assertEquals(TodoPriority.Medium, newDataSource().get("a")?.priority)
        assertEquals(
            listOf(
                TodoRecord("a", "Tâche", isDone = false, updatedAt = 2_000L, priority = TodoPriority.Medium, position = "a0"),
                TodoRecord("b", "Autre", isDone = false, updatedAt = 3_000L, position = "Zz"),
            ),
            source.pendingChanges(),
        )
        // Rien ne change : rien ne part.
        source.markSent(source.pendingChanges())
        source.setPriority("a", TodoPriority.Medium)
        source.setPositions(mapOf("b" to "Zz"))
        assertEquals(emptyList(), source.pendingChanges())
    }

    @Test
    fun applyRemoteBringsPriorityAndPositionAndIgnoresAnInvalidKey() = scope.runTest {
        val source = newDataSource()
        source.upsert(Todo("a", "Tâche", isDone = false, position = "a0"))
        source.upsert(Todo("b", "Autre", isDone = false, position = "a1"))
        source.upsert(Todo("c", "Encore", isDone = false, position = "a2"))
        source.markSent(source.pendingChanges())

        source.applyRemote(
            listOf(
                TodoRecord("c", "Encore", isDone = false, updatedAt = 5_000L, priority = TodoPriority.High, position = "Zz"),
                // Serveur qui ne connaît pas l'ordre : la tâche garde sa clé.
                TodoRecord("b", "Autre, renommée", isDone = false, updatedAt = 5_000L),
                // Clé que l'app n'aurait pas pu écrire : version ignorée.
                TodoRecord("a", "Piège", isDone = false, updatedAt = 5_000L, position = "a0'; DROP"),
                TodoRecord("new", "Créée ailleurs", isDone = false, updatedAt = 5_000L, position = "x".repeat(65)),
            ),
        )

        val todos = source.observe().first()
        assertEquals(listOf("c", "a", "b"), todos.map { it.id })
        assertEquals(TodoPriority.High, todos.first().priority)
        assertEquals("a1", source.get("b")?.position)
        assertEquals("Autre, renommée", source.get("b")?.title)
        assertEquals("Tâche", source.get("a")?.title)
        assertEquals(emptyList(), source.pendingChanges())
    }
}

private val office = LocationReminder(48.856614, 2.352222, 200, "Bureau")

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
