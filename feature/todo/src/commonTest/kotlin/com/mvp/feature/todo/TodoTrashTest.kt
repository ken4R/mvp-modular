package com.mvp.feature.todo

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.database.testing.createInMemoryDriver
import com.mvp.feature.todo.data.db.TodoDatabase
import com.mvp.feature.todo.data.repository.TodoRepositoryImpl
import com.mvp.feature.todo.data.repository.TodoTrashRepositoryImpl
import com.mvp.feature.todo.data.source.InMemoryTodoLocalDataSource
import com.mvp.feature.todo.data.source.SqlDelightTodoLocalDataSource
import com.mvp.feature.todo.data.sync.FakeTodoRemoteDataSource
import com.mvp.feature.todo.data.sync.TodoRecord
import com.mvp.feature.todo.data.sync.TodoSyncRepositoryImpl
import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoReminder
import com.mvp.feature.todo.domain.model.TodoTrash
import com.mvp.feature.todo.domain.model.TrashItem
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoPermanentlyUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.EmptyTrashUseCase
import com.mvp.feature.todo.domain.usecase.FindTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTrashUseCase
import com.mvp.feature.todo.domain.usecase.PurgeExpiredTrashUseCase
import com.mvp.feature.todo.domain.usecase.RestoreTodoUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoReminderUseCase
import com.mvp.feature.todo.domain.usecase.SyncTodoRemindersUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Corbeille avec annulation (ADR 0025), sur la vraie base SQLite et sa file d'envoi. */
class TodoTrashTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val drivers = mutableListOf<SqlDriver>()
    private val connectivity = FakeConnectivityObserver(online = true)
    private val server = FakeTodoRemoteDataSource(connectivity, latencyMillis = 0)

    /** Heure courante (epoch ms), avancée par les tests ; commune aux « appareils ». */
    private var clock = 1_000_000L

    private inner class Device {
        val files = FakeFileStore()
        val local = SqlDelightTodoLocalDataSource(
            TodoDatabase(createInMemoryDriver(TodoDatabase.Schema).also { drivers += it }),
            dispatcher,
            files,
            now = { clock },
        )
        val sync = TodoSyncRepositoryImpl(local, server)
        private var nextId = 0
        val repository = TodoRepositoryImpl(local, idGenerator = { "00000000-0000-4000-8000-00000000000${nextId++}" })
        val trashRepository = TodoTrashRepositoryImpl(local)
        val add = AddTodoUseCase(repository, FakeAnalyticsTracker(), dispatcher)
        val delete = DeleteTodoUseCase(repository, dispatcher)
        val restore = RestoreTodoUseCase(trashRepository, dispatcher)
        val deletePermanently = DeleteTodoPermanentlyUseCase(trashRepository, dispatcher)
        val emptyTrash = EmptyTrashUseCase(trashRepository, dispatcher)
        val purge = PurgeExpiredTrashUseCase(trashRepository, { clock }, dispatcher)
        val observeTrash = ObserveTrashUseCase(trashRepository, { clock }, dispatcher)
        val find = FindTodoUseCase(repository, dispatcher)
        val toggle = ToggleTodoUseCase(repository, FakeAnalyticsTracker(), dispatcher)

        suspend fun todos(): List<Todo> = local.observe().first()
        suspend fun trash(): List<TrashItem> = observeTrash(NoParams).first().getOrNull()!!
        suspend fun addTodo(title: String): Todo = add(title).getOrNull()!!
    }

    @AfterTest
    fun closeDrivers() = drivers.forEach { it.close() }

    @Test
    fun deletedTodoGoesToTrashAndUndoPutsItBackInPlaceWithItsAttachments() = scope.runTest {
        val phone = Device()
        val first = phone.addTodo("Acheter du pain")
        val second = phone.addTodo("Appeler Paul")
        phone.addTodo("Réserver le train")
        phone.local.addAttachment("p1", second.id, "Photo.jpg", AttachmentType.Photo, byteArrayOf(1, 2))

        assertEquals(AppResult.Success(Unit), phone.delete(second.id))

        assertEquals(listOf("Acheter du pain", "Réserver le train"), phone.todos().map { it.title })
        assertEquals(listOf(TrashItem(second.copy(attachmentCount = 1), daysLeft = 30)), phone.trash())
        // Les pièces jointes restent tant que la tâche est restaurable.
        assertEquals(listOf("p1"), phone.files.names())

        val restored = phone.restore(second.id)

        assertEquals(AppResult.Success(second.copy(attachmentCount = 1)), restored)
        assertEquals(listOf(first.id, second.id), phone.todos().take(2).map { it.id })
        assertEquals(emptyList(), phone.trash())
        assertEquals(1, phone.local.observeAttachments(second.id).first().size)
    }

    @Test
    fun trashedTodoIsHiddenAndCannotBeChangedUntilRestored() = scope.runTest {
        val phone = Device()
        val todo = phone.addTodo("Tâche")
        phone.delete(todo.id)

        assertEquals(AppResult.Failure(AppError.NotFound), phone.find(todo.id))
        assertEquals(AppResult.Failure(AppError.NotFound), phone.toggle(todo.id))
        assertEquals(AppResult.Failure(AppError.NotFound), phone.delete(todo.id))
        assertNull(phone.local.get(todo.id))
        assertNull(phone.local.setNote(todo.id, "Note"))
        assertNull(phone.local.addAttachment("p1", todo.id, "Photo.jpg", AttachmentType.Photo, byteArrayOf(1)))
        assertEquals(emptyList(), phone.files.names())
        // La tâche jetée garde son contenu, intact, dans la corbeille.
        assertEquals(listOf(todo), phone.trash().map { it.todo })
    }

    @Test
    fun completedTodoInTrashLeavesTheStatistics() = scope.runTest {
        val phone = Device()
        val todo = phone.addTodo("Tâche")
        phone.toggle(todo.id)
        assertEquals(1, phone.local.observeCompletionTimes().first().size)

        phone.delete(todo.id)
        assertEquals(emptyList(), phone.local.observeCompletionTimes().first())

        phone.restore(todo.id)
        assertEquals(1, phone.local.observeCompletionTimes().first().size)
    }

    @Test
    fun restoringATodoNotInTheTrashFails() = scope.runTest {
        val phone = Device()
        val todo = phone.addTodo("Tâche")

        assertEquals(AppResult.Failure(AppError.NotFound), phone.restore(todo.id))
        assertEquals(AppResult.Failure(AppError.NotFound), phone.restore("inconnue"))
        phone.delete(todo.id)
        phone.restore(todo.id)
        assertEquals(AppResult.Failure(AppError.NotFound), phone.restore(todo.id))
    }

    @Test
    fun trashListsTheLastDeletedFirstWithTheDaysLeft() = scope.runTest {
        val phone = Device()
        val old = phone.addTodo("Ancienne")
        val recent = phone.addTodo("Récente")
        phone.delete(old.id)
        clock += 10 * TodoTrash.DAY_MILLIS
        phone.delete(recent.id)
        clock += TodoTrash.DAY_MILLIS / 2

        assertEquals(listOf(recent.id to 30, old.id to 20), phone.trash().map { it.todo.id to it.daysLeft })

        // Délai écoulé : plus listée, même avant la purge.
        clock += 20 * TodoTrash.DAY_MILLIS
        assertEquals(listOf(recent.id), phone.trash().map { it.todo.id })
    }

    @Test
    fun daysLeftAreRoundedUpAndBounded() {
        val day = TodoTrash.DAY_MILLIS
        assertEquals(30, TodoTrash.daysLeft(trashedAt = 0, now = 0))
        assertEquals(30, TodoTrash.daysLeft(trashedAt = 0, now = day - 1))
        assertEquals(29, TodoTrash.daysLeft(trashedAt = 0, now = day))
        assertEquals(1, TodoTrash.daysLeft(trashedAt = 0, now = 30 * day - 1))
        assertEquals(0, TodoTrash.daysLeft(trashedAt = 0, now = 30 * day))
        // Mise à la corbeille datée dans le futur (horloge reculée) : jamais plus de 30 jours.
        assertEquals(30, TodoTrash.daysLeft(trashedAt = 100 * day, now = 0))
        assertEquals(30, TodoTrash.daysLeft(trashedAt = Long.MAX_VALUE, now = 0))
    }

    @Test
    fun aTrashTimeInTheFutureCannotDelayThePurge() = scope.runTest {
        val phone = Device()
        val future = clock + 1_000 * TodoTrash.DAY_MILLIS
        val received = "00000000-0000-4000-8000-0000000000cc"
        // Reçue d'un serveur (ou d'un appareil à l'horloge en avance) : mise à la corbeille dans le futur.
        phone.local.applyRemote(listOf(TodoRecord(received, "Reçue", isDone = false, updatedAt = future, trashedAt = future)))
        // Reçue hors corbeille avec une heure en avance, puis jetée ici.
        val local = "00000000-0000-4000-8000-0000000000dd"
        phone.local.applyRemote(listOf(TodoRecord(local, "Jetée ici", isDone = false, updatedAt = future)))
        phone.delete(local)

        clock += 30 * TodoTrash.DAY_MILLIS
        assertEquals(AppResult.Success(2), phone.purge(NoParams))
        assertEquals(emptyList(), phone.trash())
    }

    @Test
    fun purgeDeletesOnlyTodosTrashedMoreThanThirtyDaysAgo() = scope.runTest {
        val phone = Device()
        val expired = phone.addTodo("Expirée")
        val kept = phone.addTodo("Gardée")
        val active = phone.addTodo("Active")
        phone.local.addAttachment("p1", expired.id, "Photo.jpg", AttachmentType.Photo, byteArrayOf(1))
        clock += TodoTrash.DAY_MILLIS
        phone.delete(expired.id)
        clock += TodoTrash.DAY_MILLIS
        phone.delete(kept.id)

        clock += 29 * TodoTrash.DAY_MILLIS
        assertEquals(AppResult.Success(1), phone.purge(NoParams))

        assertEquals(listOf(kept.id), phone.trash().map { it.todo.id })
        assertEquals(listOf(active.id), phone.todos().map { it.id })
        assertEquals(emptyList(), phone.files.names())
        assertEquals(AppResult.Success(0), phone.purge(NoParams))
    }

    @Test
    fun deletingPermanentlyWorksOnlyFromTheTrash() = scope.runTest {
        val phone = Device()
        val todo = phone.addTodo("Tâche")
        phone.local.addAttachment("p1", todo.id, "Photo.jpg", AttachmentType.Photo, byteArrayOf(1))

        assertEquals(AppResult.Failure(AppError.NotFound), phone.deletePermanently(todo.id))
        assertEquals(1, phone.todos().size)

        phone.delete(todo.id)
        assertEquals(AppResult.Success(Unit), phone.deletePermanently(todo.id))

        assertEquals(emptyList(), phone.trash())
        assertEquals(emptyList(), phone.files.names())
        assertEquals(AppResult.Failure(AppError.NotFound), phone.restore(todo.id))
    }

    @Test
    fun aFileThatCannotBeDeletedDoesNotFailThePermanentDeletion() = scope.runTest {
        val phone = Device()
        val todo = phone.addTodo("Tâche")
        phone.local.addAttachment("p1", todo.id, "Photo.jpg", AttachmentType.Photo, byteArrayOf(1))
        phone.delete(todo.id)
        phone.files.deleteFailure = IllegalStateException("disque")

        assertEquals(AppResult.Success(Unit), phone.deletePermanently(todo.id))
        assertEquals(emptyList(), phone.trash())
    }

    @Test
    fun emptyingTheTrashKeepsTheListedTodos() = scope.runTest {
        val phone = Device()
        val a = phone.addTodo("A")
        val b = phone.addTodo("B")
        val c = phone.addTodo("C")
        phone.delete(a.id)
        phone.delete(b.id)

        assertEquals(AppResult.Success(2), phone.emptyTrash(NoParams))

        assertEquals(emptyList(), phone.trash())
        assertEquals(listOf(c.id), phone.todos().map { it.id })
    }

    @Test
    fun trashRestoreAndPermanentDeletionReachTheOtherDevices() = scope.runTest {
        val phone = Device()
        val tablet = Device()
        val todo = phone.addTodo("Tâche")
        phone.sync.sync()
        tablet.sync.sync()

        clock += 1_000
        phone.delete(todo.id)
        phone.sync.sync()
        tablet.sync.sync()
        assertEquals(emptyList(), tablet.todos())
        assertEquals(listOf(TrashItem(todo, daysLeft = 30)), tablet.trash())

        clock += 1_000
        tablet.restore(todo.id)
        tablet.sync.sync()
        phone.sync.sync()
        assertEquals(listOf(todo.id), phone.todos().map { it.id })
        assertEquals(emptyList(), phone.trash())

        clock += 1_000
        phone.delete(todo.id)
        phone.deletePermanently(todo.id)
        phone.sync.sync()
        tablet.sync.sync()
        assertEquals(emptyList(), tablet.todos())
        assertEquals(emptyList(), tablet.trash())
    }

    @Test
    fun anEditMadeElsewhereAfterTheDeletionWinsLikeAnyChange() = scope.runTest {
        val phone = Device()
        val tablet = Device()
        val todo = phone.addTodo("Tâche")
        phone.sync.sync()
        tablet.sync.sync()

        connectivity.online.value = false
        clock += 1_000
        phone.delete(todo.id)
        clock += 1_000
        tablet.local.setNote(todo.id, "Plus récente")
        connectivity.online.value = true
        phone.sync.sync()
        tablet.sync.sync()
        phone.sync.sync()

        // Comme tout conflit (ADR 0012), la modification la plus récente gagne : la tâche sort de la corbeille.
        assertEquals("Plus récente", phone.todos().single().note)
        assertEquals(emptyList(), phone.trash())
    }

    @Test
    fun remoteVersionTrashedAfterItsOwnChangeIsIgnored() = scope.runTest {
        val phone = Device()
        val id = "00000000-0000-4000-8000-0000000000aa"
        phone.local.applyRemote(
            listOf(
                TodoRecord(id, "Tâche", isDone = false, updatedAt = 5_000L, trashedAt = 6_000L),
                TodoRecord("00000000-0000-4000-8000-0000000000bb", "Jetée", isDone = false, updatedAt = 5_000L, trashedAt = 5_000L),
            ),
        )

        assertNull(phone.local.get(id))
        assertEquals(listOf("Jetée"), phone.trash().map { it.todo.title })
    }

    @Test
    fun trashedStateIsSentWithThePendingChange() = scope.runTest {
        val phone = Device()
        val todo = phone.addTodo("Tâche")
        phone.local.markSent(phone.local.pendingChanges())
        clock = 2_000_000L

        phone.delete(todo.id)

        val change = phone.local.pendingChanges().single()
        assertEquals(2_000_000L, change.trashedAt)
        assertEquals(change.updatedAt, change.trashedAt)
        assertEquals(false, change.isDeleted)

        phone.restore(todo.id)
        assertNull(phone.local.pendingChanges().single().trashedAt)
    }

    @Test
    fun signingOutEmptiesTheTrash() = scope.runTest {
        val phone = Device()
        val todo = phone.addTodo("Tâche")
        phone.local.addAttachment("p1", todo.id, "Photo.jpg", AttachmentType.Photo, byteArrayOf(1))
        phone.delete(todo.id)

        phone.local.clear()

        assertEquals(emptyList(), phone.trash())
        assertEquals(emptyList(), phone.files.names())
    }

    @Test
    fun migrationToVersion9PutsNoTodoInTheTrash() = scope.runTest {
        val driver = createInMemoryDriver(TrashSchemaV1).also { drivers += it }
        TodoDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 8)
        driver.execute(null, "INSERT INTO TodoEntity(id, title, is_done) VALUES ('a', 'Ancienne', 0)", 0)

        TodoDatabase.Schema.migrate(driver, oldVersion = 8, newVersion = TodoDatabase.Schema.version)
        val source = SqlDelightTodoLocalDataSource(TodoDatabase(driver), dispatcher, FakeFileStore(), now = { clock })

        assertEquals(9L, TodoDatabase.Schema.version)
        assertEquals(listOf("a"), source.observe().first().map { it.id })
        assertEquals(emptyList(), source.observeTrash().first())
        assertTrue(source.trash("a"))
        assertEquals(listOf("a"), source.observeTrash().first().map { it.todo.id })
    }

    @Test
    fun remindersOfATrashedTodoAreCancelledAndComeBackOnRestore() = scope.runTest {
        val now = 1_000_000L
        val local = InMemoryTodoLocalDataSource(now = { now })
        val repository = TodoRepositoryImpl(local, idGenerator = { "id-0" })
        val trash = TodoTrashRepositoryImpl(local)
        val scheduler = FakeReminderScheduler()
        val reminders = SyncTodoRemindersUseCase(
            repository,
            FakeNotificationsPreference(enabled = true),
            FakeSessionExpiry(expiresAt = Long.MAX_VALUE),
            scheduler,
            { now },
            dispatcher,
        )
        AddTodoUseCase(repository, FakeAnalyticsTracker(), dispatcher)("Appeler Paul")
        SetTodoReminderUseCase(repository, { now }, dispatcher)(TodoReminder("id-0", at = now + 60_000))
        val job = launch { reminders(NoParams).collect {} }
        runCurrent()
        assertEquals(listOf("id-0"), scheduler.scheduled.map { it.id })

        DeleteTodoUseCase(repository, dispatcher)("id-0")
        runCurrent()
        assertEquals(emptyList(), scheduler.scheduled.map { it.id })

        RestoreTodoUseCase(trash, dispatcher)("id-0")
        runCurrent()
        assertEquals(listOf("id-0"), scheduler.scheduled.map { it.id })
        job.cancel()
    }
}

/** Schéma de `todo.db` avant les rappels (version 1), pour tester la migration jusqu'à la corbeille. */
private object TrashSchemaV1 : SqlSchema<QueryResult.Value<Unit>> {
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
