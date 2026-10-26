package com.mvp.feature.todo

import app.cash.sqldelight.db.SqlDriver
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.database.testing.createInMemoryDriver
import com.mvp.feature.todo.data.db.TodoDatabase
import com.mvp.feature.todo.data.session.TodoUserSessionObserver
import com.mvp.feature.todo.data.source.SqlDelightTodoLocalDataSource
import com.mvp.feature.todo.data.sync.FakeTodoRemoteDataSource
import com.mvp.feature.todo.data.sync.TodoRecord
import com.mvp.feature.todo.data.sync.TodoRemoteDataSource
import com.mvp.feature.todo.data.sync.TodoSyncRepositoryImpl
import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoSyncStatus
import com.mvp.feature.todo.domain.usecase.ObserveTodoSyncStatusUseCase
import com.mvp.feature.todo.domain.usecase.StartTodoSyncUseCase
import com.mvp.feature.todo.domain.usecase.SyncTodosUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Mode hors ligne (ADR 0012) : deux « appareils » (deux bases) partagent le même faux serveur, avec une
 * horloge commune pilotée par le test.
 */
class TodoSyncTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val connectivity = FakeConnectivityObserver(online = true)
    private val session = FakeSessionExpiry(expiresAt = Long.MAX_VALUE)
    private val server = FakeTodoRemoteDataSource(connectivity, latencyMillis = 0)
    private var clock = 1_000L
    private val drivers = mutableListOf<SqlDriver>()

    private inner class Device {
        val files = FakeFileStore()
        val local = SqlDelightTodoLocalDataSource(
            TodoDatabase(createInMemoryDriver(TodoDatabase.Schema).also { drivers += it }),
            dispatcher,
            files,
            now = { clock },
        )
        val sync = TodoSyncRepositoryImpl(local, server)
        suspend fun todos(): List<Todo> = local.observe().first()
    }

    @AfterTest
    fun closeDrivers() = drivers.forEach { it.close() }

    @Test
    fun changesMadeOfflineReachAnotherDeviceOnceBackOnline() = scope.runTest {
        val phone = Device()
        val tablet = Device()
        connectivity.online.value = false
        phone.local.upsert(Todo("a", "Acheter du pain", isDone = false))
        phone.local.upsert(Todo("b", "Appeler Paul", isDone = false))

        assertEquals(AppResult.Failure(AppError.Network), phone.sync.sync())
        assertEquals(2, phone.local.pendingChanges().size)

        connectivity.online.value = true
        assertEquals(AppResult.Success(0), phone.sync.sync())
        tablet.sync.sync()

        assertEquals(phone.todos(), tablet.todos())
        assertEquals(emptyList(), phone.local.pendingChanges())
    }

    @Test
    fun dueDateTravelsWithTheTaskButTheReminderStaysOnTheDevice() = scope.runTest {
        val phone = Device()
        val tablet = Device()
        phone.local.upsert(Todo("a", "Acheter du pain", isDone = false))
        phone.sync.sync()
        tablet.sync.sync()
        clock = 2_000L
        phone.local.setDueDate("a", 20_754)
        phone.local.setReminder("a", 42L)
        phone.sync.sync()

        tablet.sync.sync()

        assertEquals(listOf(Todo("a", "Acheter du pain", isDone = false, dueDate = 20_754)), tablet.todos())
    }

    @Test
    fun noteTravelsWithTheTaskButAttachmentsStayOnTheDevice() = scope.runTest {
        val phone = Device()
        val tablet = Device()
        phone.local.upsert(Todo("a", "Acheter du pain", isDone = false))
        phone.local.addAttachment("p1", "a", "Photo.jpg", AttachmentType.Photo, byteArrayOf(1, 2))
        clock = 2_000L
        phone.local.setNote("a", "Complet, pas de seigle")
        phone.sync.sync()

        tablet.sync.sync()

        assertEquals(listOf(Todo("a", "Acheter du pain", isDone = false, note = "Complet, pas de seigle")), tablet.todos())
        assertEquals(emptyList(), tablet.files.names())
        assertEquals(1, phone.todos().single().attachmentCount)
    }

    @Test
    fun aTaskDeletedOnAnotherDeviceLosesItsAttachmentsHere() = scope.runTest {
        val phone = Device()
        val tablet = Device()
        phone.local.upsert(Todo("a", "Tâche", isDone = false))
        phone.sync.sync()
        tablet.sync.sync()
        tablet.local.addAttachment("p1", "a", "Photo.jpg", AttachmentType.Photo, byteArrayOf(1, 2))

        clock = 2_000L
        phone.local.delete("a")
        phone.sync.sync()
        tablet.sync.sync()

        assertEquals(emptyList(), tablet.todos())
        assertEquals(emptyList(), tablet.local.observeAttachments("a").first())
        assertEquals(emptyList(), tablet.files.names())
    }

    @Test
    fun conflictKeepsTheMostRecentChange() = scope.runTest {
        val phone = Device()
        val tablet = Device()
        phone.local.upsert(Todo("a", "Tâche", isDone = false))
        phone.sync.sync()
        tablet.sync.sync()

        // Hors ligne, chacun modifie la même tâche ; la tablette le fait en dernier.
        connectivity.online.value = false
        clock = 2_000L
        phone.local.upsert(Todo("a", "Titre du téléphone", isDone = false))
        clock = 3_000L
        tablet.local.toggle("a")
        connectivity.online.value = true

        // La tablette synchronise d'abord, puis le téléphone envoie une version plus ancienne.
        tablet.sync.sync()
        phone.sync.sync()
        tablet.sync.sync()

        val expected = listOf(Todo("a", "Tâche", isDone = true))
        assertEquals(expected, phone.todos())
        assertEquals(expected, tablet.todos())
    }

    @Test
    fun deletionReachesOtherDevicesAndAnOlderEditDoesNotResurrectIt() = scope.runTest {
        val phone = Device()
        val tablet = Device()
        phone.local.upsert(Todo("a", "Tâche", isDone = false))
        phone.sync.sync()
        tablet.sync.sync()

        connectivity.online.value = false
        clock = 2_000L
        tablet.local.toggle("a")
        clock = 3_000L
        phone.local.delete("a")
        connectivity.online.value = true
        phone.sync.sync()
        tablet.sync.sync()
        phone.sync.sync()

        assertEquals(emptyList(), tablet.todos())
        assertEquals(emptyList(), phone.todos())
    }

    @Test
    fun statusShowsOfflinePendingChangesAndFailure() = scope.runTest {
        val phone = Device()
        val status = ObserveTodoSyncStatusUseCase(phone.sync, connectivity, dispatcher)
        connectivity.online.value = false
        phone.local.upsert(Todo("a", "Tâche", isDone = false))
        phone.sync.sync()

        assertEquals(
            TodoSyncStatus(isOnline = false, pendingChanges = 1, isSyncing = false, lastSyncFailed = true),
            status(NoParams).first().getOrNull(),
        )

        connectivity.online.value = true
        phone.sync.sync()

        assertEquals(
            TodoSyncStatus(isOnline = true, pendingChanges = 0, isSyncing = false, lastSyncFailed = false),
            status(NoParams).first().getOrNull(),
        )
    }

    @Test
    fun manualSyncFailsOfflineWithoutCallingTheServer() = scope.runTest {
        val phone = Device()
        val syncNow = SyncTodosUseCase(phone.sync, connectivity, dispatcher)
        phone.local.upsert(Todo("a", "Tâche", isDone = false))
        connectivity.online.value = false

        assertEquals(AppResult.Failure(AppError.Network), syncNow(NoParams))
        // Sans appel au serveur, la synchronisation n'est pas marquée en échec.
        assertEquals(false, phone.sync.observeSyncState().first().lastSyncFailed)

        connectivity.online.value = true
        assertEquals(AppResult.Success(0), syncNow(NoParams))
    }

    @Test
    fun automaticSyncRunsAtStartOnEachChangeAndWhenTheNetworkComesBack() = scope.runTest {
        val phone = Device()
        val tablet = Device()
        val job = launch { StartTodoSyncUseCase(phone.sync, connectivity, session, dispatcher)(NoParams).collect {} }
        testScheduler.advanceUntilIdle()

        phone.local.upsert(Todo("a", "En ligne", isDone = false))
        testScheduler.advanceUntilIdle()
        tablet.sync.sync()
        assertEquals(listOf("En ligne"), tablet.todos().map { it.title })

        connectivity.online.value = false
        phone.local.upsert(Todo("b", "Hors ligne", isDone = false))
        testScheduler.advanceUntilIdle()
        assertEquals(1, phone.local.pendingChanges().size)

        connectivity.online.value = true
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(emptyList(), phone.local.pendingChanges())
        tablet.sync.sync()
        assertEquals(listOf("En ligne", "Hors ligne"), tablet.todos().map { it.title })
    }

    @Test
    fun automaticSyncRetriesWithGrowingDelays() = scope.runTest {
        val phone = Device()
        val failingServer = object : TodoRemoteDataSource {
            var calls = 0
            override suspend fun push(changes: List<TodoRecord>): AppResult<Unit> {
                calls++
                return AppResult.Failure(AppError.Network)
            }
            override suspend fun fetchAll() = AppResult.Failure(AppError.Network)
            override suspend fun signOut() {}
        }
        val sync = TodoSyncRepositoryImpl(phone.local, failingServer)
        phone.local.upsert(Todo("a", "Tâche", isDone = false))
        val job = launch { StartTodoSyncUseCase(sync, connectivity, session, dispatcher)(NoParams).collect {} }

        testScheduler.advanceTimeBy(1)
        assertEquals(1, failingServer.calls)
        // 2 s, puis 4 s, puis 8 s : quatre essais en 14 s.
        testScheduler.advanceTimeBy(14_000)
        assertEquals(4, failingServer.calls)

        // Hors ligne, plus aucun essai.
        connectivity.online.value = false
        testScheduler.advanceTimeBy(600_000)
        assertEquals(4, failingServer.calls)
        job.cancel()
    }

    @Test
    fun noSyncWithoutSession() = scope.runTest {
        val phone = Device()
        session.expiresAt.value = null
        phone.local.upsert(Todo("a", "Tâche", isDone = false))
        val job = launch { StartTodoSyncUseCase(phone.sync, connectivity, session, dispatcher)(NoParams).collect {} }
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(1, phone.local.pendingChanges().size)
    }

    @Test
    fun logoutDuringASyncWaitsForItAndLeavesNothingBehind() = scope.runTest {
        val slowServer = FakeTodoRemoteDataSource(connectivity, latencyMillis = 400)
        val phone = Device()
        val sync = TodoSyncRepositoryImpl(phone.local, slowServer)
        val observer = TodoUserSessionObserver(phone.local, FakeReminderScheduler(), sync)
        phone.local.upsert(Todo("a", "Tâche", isDone = false))

        val running = launch { sync.sync() }
        testScheduler.advanceTimeBy(200)
        val logout = launch { observer.clearUserData() }
        testScheduler.advanceUntilIdle()
        running.join()
        logout.join()

        assertEquals(emptyList(), phone.todos())
        assertEquals(emptyList(), phone.local.pendingChanges())
        assertTrue((slowServer.fetchAll() as AppResult.Success).data.isEmpty())
    }

    @Test
    fun automaticSyncStopsRetryingWhenTheServerRefusesTheSession() = scope.runTest {
        val phone = Device()
        var calls = 0
        val refusingServer = object : TodoRemoteDataSource {
            override suspend fun push(changes: List<TodoRecord>): AppResult<Unit> {
                calls++
                return AppResult.Failure(AppError.Unauthorized)
            }
            override suspend fun fetchAll() = AppResult.Failure(AppError.Unauthorized)
            override suspend fun signOut() {}
        }
        phone.local.upsert(Todo("a", "Tâche", isDone = false))
        val job = launch { StartTodoSyncUseCase(TodoSyncRepositoryImpl(phone.local, refusingServer), connectivity, session, dispatcher)(NoParams).collect {} }

        testScheduler.advanceTimeBy(600_000)
        job.cancel()

        assertEquals(1, calls)
    }

    @Test
    fun automaticSyncStopsAtLogoutAndResumesRightAfterTheNextLogin() = scope.runTest {
        val phone = Device()
        val observer = TodoUserSessionObserver(phone.local, FakeReminderScheduler(), phone.sync)
        val job = launch { StartTodoSyncUseCase(phone.sync, connectivity, session, dispatcher)(NoParams).collect {} }
        testScheduler.advanceUntilIdle()
        observer.clearUserData()
        testScheduler.advanceUntilIdle()

        // La session de l'autre compte est enregistrée avant onLogin : rien ne part encore.
        phone.local.upsert(Todo("a", "Nouveau compte", isDone = false))
        testScheduler.advanceUntilIdle()
        assertEquals(1, phone.local.pendingChanges().size)

        observer.onLogin(UserAccount("2", "Autre", "autre@example.com"))
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(emptyList(), phone.local.pendingChanges())
    }

    @Test
    fun logoutErasesTodosOutboxAndServerAndBlocksSyncUntilNextLogin() = scope.runTest {
        val phone = Device()
        val reminders = FakeReminderScheduler()
        val observer = TodoUserSessionObserver(phone.local, reminders, phone.sync)
        phone.local.upsert(Todo("a", "Envoyée", isDone = false))
        phone.sync.sync()
        connectivity.online.value = false
        phone.local.upsert(Todo("b", "En attente", isDone = false))
        connectivity.online.value = true

        assertEquals(AppResult.Success(Unit), observer.clearUserData())

        assertEquals(emptyList(), phone.todos())
        assertEquals(emptyList(), phone.local.pendingChanges())
        assertEquals(AppResult.Failure(AppError.Unauthorized), phone.sync.sync())

        // Le compte suivant ne récupère rien du précédent.
        observer.onLogin(UserAccount("2", "Autre", "autre@example.com"))
        assertEquals(AppResult.Success(0), phone.sync.sync())
        assertEquals(emptyList(), phone.todos())
        assertIs<AppResult.Success<List<*>>>(server.fetchAll())
        assertTrue((server.fetchAll() as AppResult.Success).data.isEmpty())
    }
}
