package com.mvp.core.analytics

import com.mvp.core.analytics.data.QueuedAnalyticsRepository
import com.mvp.core.analytics.data.session.AnalyticsUserSessionObserver
import com.mvp.core.analytics.data.sink.AnalyticsSink
import com.mvp.core.analytics.data.sink.DebugAnalyticsSink
import com.mvp.core.analytics.domain.model.AnalyticsJournal
import com.mvp.core.analytics.domain.model.TrackedEvent
import com.mvp.core.analytics.domain.usecase.FlushAnalyticsUseCase
import com.mvp.core.analytics.domain.usecase.ObserveAnalyticsJournalUseCase
import com.mvp.core.analytics.domain.usecase.TrackEventUseCase
import com.mvp.core.common.domain.analytics.AnalyticsConsent
import com.mvp.core.common.domain.analytics.AnalyticsEvent
import com.mvp.core.common.domain.analytics.AnalyticsScreen
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.usecase.NoParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AnalyticsTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val consent = FakeAnalyticsConsent()
    private val sink = FailingDebugSink()
    private var clock = 1_000L
    private var sessions = 0
    private val repository = QueuedAnalyticsRepository(
        consent = consent,
        sink = sink,
        sentEvents = sink.observeSent(),
        now = { clock },
        newSessionId = { "session-${sessions++}" },
        scope = scope.backgroundScope,
        batchSize = 3,
        maxPending = 5,
    )

    private val track = TrackEventUseCase(repository, dispatcher)
    private val flush = FlushAnalyticsUseCase(repository, dispatcher)
    private val observeJournal = ObserveAnalyticsJournalUseCase(repository, dispatcher)

    private suspend fun journal(): AnalyticsJournal = observeJournal(NoParams).first().getOrNull()!!

    @Test
    fun nothingIsKeptWithoutConsent() = scope.runTest {
        track(AnalyticsEvent.TaskAdded)
        assertEquals(AppResult.Success(0), flush(NoParams))

        assertEquals(AnalyticsJournal(pending = emptyList(), sent = emptyList()), journal())
    }

    @Test
    fun consentedEventsWaitThenAreSentDated() = scope.runTest {
        consent.granted = true
        track(AnalyticsEvent.ScreenViewed(AnalyticsScreen.Weather))
        clock = 2_000L
        track(AnalyticsEvent.TaskAdded)

        assertEquals(2, journal().pending.size)
        assertEquals(emptyList(), journal().sent)

        assertEquals(AppResult.Success(2), flush(NoParams))

        assertEquals(
            listOf(
                TrackedEvent(1, "task_added", emptyMap(), timestamp = 2_000L, sessionId = "session-0"),
                TrackedEvent(0, "screen_viewed", mapOf("screen" to "weather"), timestamp = 1_000L, sessionId = "session-0"),
            ),
            journal().sent,
        )
        assertEquals(emptyList(), journal().pending)
    }

    @Test
    fun fullBatchIsSentWithoutWaiting() = scope.runTest {
        consent.granted = true
        repeat(3) { track(AnalyticsEvent.TaskAdded) }
        runCurrent()

        assertEquals(3, journal().sent.size)
        assertEquals(emptyList(), journal().pending)
    }

    @Test
    fun revokingDropsPendingEventsAndStartsANewSession() = scope.runTest {
        consent.granted = true
        track(AnalyticsEvent.TaskAdded)

        consent.granted = false
        repository.onAnalyticsConsentRevoked()
        assertEquals(emptyList(), journal().pending)

        consent.granted = true
        track(AnalyticsEvent.TaskCompleted)
        flush(NoParams)
        assertEquals(listOf("session-1"), journal().sent.map { it.sessionId })
    }

    @Test
    fun revokingAlsoResetsTheTool() = scope.runTest {
        consent.granted = true
        track(AnalyticsEvent.TaskAdded)
        flush(NoParams)

        repository.onAnalyticsConsentRevoked()

        assertEquals(AnalyticsJournal(pending = emptyList(), sent = emptyList()), journal())
    }

    @Test
    fun eventReadAsConsentedJustBeforeARevocationIsDropped() = scope.runTest {
        consent.granted = true
        consent.duringCheck = {
            consent.granted = false
            repository.onAnalyticsConsentRevoked()
        }

        track(AnalyticsEvent.TaskAdded)

        // Le consentement a été lu « accordé », mais le retrait est passé avant la mise en file.
        consent.granted = true
        flush(NoParams)
        assertEquals(AnalyticsJournal(pending = emptyList(), sent = emptyList()), journal())
    }

    @Test
    fun eventReadBeforeALogoutDoesNotReachTheNextAccount() = scope.runTest {
        consent.granted = true
        consent.duringCheck = { AnalyticsUserSessionObserver(repository).clearUserData() }

        track(AnalyticsEvent.TaskAdded)

        assertEquals(emptyList(), journal().pending)
    }

    @Test
    fun consentNoLongerValidAtSendTimeDropsTheQueue() = scope.runTest {
        consent.granted = true
        track(AnalyticsEvent.TaskAdded)

        // Par exemple, nouvelle version du texte de consentement : l'accord ne vaut plus.
        consent.granted = false
        assertEquals(AppResult.Success(0), flush(NoParams))

        assertEquals(AnalyticsJournal(pending = emptyList(), sent = emptyList()), journal())
    }

    @Test
    fun failedSendKeepsEventsForTheNextOne() = scope.runTest {
        consent.granted = true
        track(AnalyticsEvent.TaskAdded)
        sink.failing = true

        assertIs<AppResult.Failure>(flush(NoParams))
        assertEquals(1, journal().pending.size)

        sink.failing = false
        assertEquals(AppResult.Success(1), flush(NoParams))
    }

    @Test
    fun trackingNeverFailsEvenWhenTheToolDoes() = scope.runTest {
        consent.granted = true
        sink.failing = true

        repeat(3) { assertEquals(AppResult.Success(Unit), track(AnalyticsEvent.TaskAdded)) }
    }

    @Test
    fun pendingQueueIsBounded() = scope.runTest {
        consent.granted = true
        sink.failing = true
        repeat(8) { track(AnalyticsEvent.TaskAdded) }

        // Les plus anciens sont abandonnés : seuls les 5 derniers attendent.
        assertEquals((7L downTo 3L).toList(), journal().pending.map { it.id })
    }

    @Test
    fun logoutWipesQueueJournalAndSession() = scope.runTest {
        consent.granted = true
        track(AnalyticsEvent.TaskAdded)
        flush(NoParams)
        track(AnalyticsEvent.TaskCompleted)

        AnalyticsUserSessionObserver(repository).clearUserData()

        assertEquals(AnalyticsJournal(pending = emptyList(), sent = emptyList()), journal())
        track(AnalyticsEvent.TaskAdded)
        assertNotEquals("session-0", journal().pending.single().sessionId)
    }

    @Test
    fun eventsCarryNoPersonalData() {
        val events = listOf(AnalyticsEvent.TaskAdded, AnalyticsEvent.TaskCompleted, AnalyticsEvent.DebugTest) +
            AnalyticsScreen.entries.map { AnalyticsEvent.ScreenViewed(it) }

        // Seule propriété possible : l'écran, parmi une liste fermée.
        assertTrue(events.all { event -> event.properties.keys.all { it == "screen" } })
        assertEquals(AnalyticsScreen.entries.map { it.key }.toSet(), events.mapNotNull { it.properties["screen"] }.toSet())
    }
}

private class FakeAnalyticsConsent : AnalyticsConsent {
    var granted = false

    /** Appelé pendant la lecture du consentement : simule un retrait qui arrive à ce moment-là. */
    var duringCheck: (suspend () -> Unit)? = null

    override suspend fun isGranted(): Boolean {
        val wasGranted = granted
        duringCheck?.let { duringCheck = null; it() }
        return wasGranted
    }
}

/** Journal de debug dont l'envoi peut échouer, comme un vrai outil injoignable. */
private class FailingDebugSink : AnalyticsSink {
    private val delegate = DebugAnalyticsSink()
    var failing = false

    fun observeSent(): Flow<List<TrackedEvent>> = delegate.observeSent()

    override suspend fun send(batch: List<TrackedEvent>) {
        if (failing) error("outil injoignable")
        delegate.send(batch)
    }

    override suspend fun reset() = delegate.reset()
}
