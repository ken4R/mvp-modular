package com.mvp.core.crashreporting

import com.mvp.core.common.domain.crash.CrashReportingConsent
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.crashreporting.data.CrashReporter
import com.mvp.core.crashreporting.data.sink.DebugCrashReportSink
import com.mvp.core.crashreporting.data.store.PendingCrashStore
import com.mvp.core.crashreporting.domain.model.CrashScrubber
import com.mvp.core.crashreporting.domain.model.CrashedException
import com.mvp.core.crashreporting.domain.usecase.ObserveCrashJournalUseCase
import com.mvp.core.crashreporting.domain.usecase.StartCrashReportingUseCase
import com.mvp.core.files.PrivateFileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReportingTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val consent = FakeCrashConsent()
    private val files = InMemoryFileStore()
    private val store = PendingCrashStore(files)
    private val sink = DebugCrashReportSink()
    private var handler: ((Throwable) -> Unit)? = null
    private var installs = 0
    private var nextId = 0

    private fun TestScope.reporter() = CrashReporter(
        consent = consent,
        store = store,
        sink = sink,
        journal = sink.observeSent(),
        now = { 42L },
        newId = { "rapport-${nextId++}" },
        platform = { "Test 1.0" },
        installHandler = { handler = it; installs++ },
        stackFrames = { listOf("com.mvp.Foo.bar(Foo.kt:12)", "com.mvp.Main.run(Main.kt:3)") },
        scope = backgroundScope,
    )

    private val start get() = StartCrashReportingUseCase(reporterForUseCases, dispatcher)
    private lateinit var reporterForUseCases: CrashReporter

    private fun crash() = handler!!(IllegalStateException("Acheter du pain chez youssef@example.com", RuntimeException("cause")))

    @Test
    fun nothingIsRecordedWithoutConsent() = scope.runTest {
        reporterForUseCases = reporter()
        start(NoParams)
        runCurrent()

        crash()

        assertTrue(files.contents.isEmpty())
    }

    @Test
    fun crashIsRecordedWithoutItsMessageThenSentAtNextLaunch() = scope.runTest {
        consent.granted.value = true
        reporterForUseCases = reporter()
        assertEquals(AppResult.Success(0), start(NoParams))
        runCurrent()

        crash()

        val written = files.contents.values.single().decodeToString()
        assertFalse("pain" in written, written)
        assertFalse("youssef" in written, written)
        assertTrue("IllegalStateException" in written, written)

        // Lancement suivant : nouveau gestionnaire, même dossier.
        val nextLaunch = StartCrashReportingUseCase(reporter(), dispatcher)
        assertEquals(AppResult.Success(1), nextLaunch(NoParams))

        val sent = sink.observeSent().first().single()
        assertEquals(42L, sent.occurredAt)
        assertEquals("Test 1.0", sent.platform)
        assertEquals(2, sent.exceptions.size)
        assertEquals(listOf("com.mvp.Foo.bar(Foo.kt:12)", "com.mvp.Main.run(Main.kt:3)"), sent.exceptions.first().frames)
        assertTrue(files.contents.isEmpty())
    }

    @Test
    fun pendingReportsAreDeletedIfConsentWasWithdrawnSince() = scope.runTest {
        consent.granted.value = true
        reporterForUseCases = reporter()
        start(NoParams)
        runCurrent()
        crash()

        consent.granted.value = false
        val nextLaunch = StartCrashReportingUseCase(reporter(), dispatcher)

        assertEquals(AppResult.Success(0), nextLaunch(NoParams))
        assertTrue(sink.observeSent().first().isEmpty())
        assertTrue(files.contents.isEmpty())
    }

    @Test
    fun revokingStopsRecordingAndDeletesPendingReports() = scope.runTest {
        consent.granted.value = true
        reporterForUseCases = reporter()
        start(NoParams)
        runCurrent()
        crash()

        reporterForUseCases.onCrashReportingConsentRevoked()
        crash()

        assertTrue(files.contents.isEmpty())
    }

    @Test
    fun handlerIsInstalledOnlyOnce() = scope.runTest {
        reporterForUseCases = reporter()
        start(NoParams)
        start(NoParams)

        assertEquals(1, installs)
    }

    @Test
    fun clearAtLogoutEmptiesPendingReportsAndJournal() = scope.runTest {
        consent.granted.value = true
        reporterForUseCases = reporter()
        start(NoParams)
        runCurrent()
        crash()
        StartCrashReportingUseCase(reporter(), dispatcher)(NoParams)
        runCurrent()
        crash()
        assertEquals(1, files.contents.size)

        reporterForUseCases.clear()

        assertTrue(files.contents.isEmpty())
        val journal = ObserveCrashJournalUseCase(reporterForUseCases, dispatcher)(NoParams).first().getOrNull()
        assertEquals(emptyList(), journal)
    }

    @Test
    fun journalShowsSentReports() = scope.runTest {
        consent.granted.value = true
        reporterForUseCases = reporter()
        start(NoParams)
        runCurrent()
        crash()
        StartCrashReportingUseCase(reporter(), dispatcher)(NoParams)

        val journal = ObserveCrashJournalUseCase(reporterForUseCases, dispatcher)(NoParams).first().getOrNull()

        assertEquals(1, journal?.size)
    }

    @Test
    fun pendingReportsAreBounded() = scope.runTest {
        consent.granted.value = true
        reporterForUseCases = reporter()
        start(NoParams)
        runCurrent()

        repeat(PendingCrashStore.DEFAULT_CAPACITY + 5) { crash() }

        assertEquals(PendingCrashStore.DEFAULT_CAPACITY, files.contents.size)
    }

    @Test
    fun unreadableReportIsDeleted() = scope.runTest {
        consent.granted.value = true
        files.write("abime", "pas du json".encodeToByteArray())

        assertEquals(AppResult.Success(0), StartCrashReportingUseCase(reporter(), dispatcher)(NoParams))
        assertTrue(files.contents.isEmpty())
    }

    @Test
    fun scrubberMasksPersonalDataAndKeepsCodeLocations() {
        val scrubbed = CrashScrubber.scrub(
            listOf(
                CrashedException(
                    type = "com.mvp.Error",
                    frames = listOf(
                        "com.mvp.Foo.bar(Foo.kt:12)",
                        "3  Shared  0x0000000104a2b3c4  kfun:com.mvp#run(){} + 52 (/Users/youssef/Documents/MVP/Main.kt:3:9)",
                        "com.mvp.Mail.send(youssef@example.com)",
                        "com.mvp.Call.dial(0612345678)",
                    ),
                ),
            ),
        ).single()

        assertEquals(
            listOf(
                "com.mvp.Foo.bar(Foo.kt:12)",
                "3  Shared  0x0000000104a2b3c4  kfun:com.mvp#run(){} + 52 (<chemin>/Main.kt:3:9)",
                "com.mvp.Mail.send(<email>)",
                "com.mvp.Call.dial(<nombre>)",
            ),
            scrubbed.frames,
        )
    }

    @Test
    fun scrubberBoundsTheReportSize() {
        val long = CrashedException(type = "x".repeat(1_000), frames = List(100) { "f" })

        val scrubbed = CrashScrubber.scrub(List(10) { long })

        assertEquals(CrashScrubber.MAX_EXCEPTIONS, scrubbed.size)
        assertEquals(CrashScrubber.MAX_LINE_LENGTH, scrubbed.first().type.length)
        assertEquals(CrashScrubber.MAX_FRAMES, scrubbed.first().frames.size)
    }
}

private class FakeCrashConsent : CrashReportingConsent {
    val granted = MutableStateFlow(false)
    override fun observeGranted(): Flow<Boolean> = granted
}

private class InMemoryFileStore : PrivateFileStore {
    val contents = linkedMapOf<String, ByteArray>()
    override fun write(name: String, bytes: ByteArray) {
        contents[name] = bytes
    }
    override fun read(name: String): ByteArray? = contents[name]
    override fun path(name: String): String = "/memoire/$name"
    override fun delete(name: String) {
        contents.remove(name)
    }
    override fun deleteAll() = contents.clear()
    override fun names(): List<String> = contents.keys.toList()
}
