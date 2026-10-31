package com.mvp.feature.focus

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.focus.data.alarm.NotificationFocusAlarm
import com.mvp.feature.focus.data.repository.SecureFocusRepository
import com.mvp.feature.focus.data.session.FocusUserSessionObserver
import com.mvp.feature.focus.domain.model.FocusDurations
import com.mvp.feature.focus.domain.model.FocusFormat
import com.mvp.feature.focus.domain.model.FocusPhase
import com.mvp.feature.focus.domain.model.FocusState
import com.mvp.feature.focus.domain.model.FocusStatus
import com.mvp.feature.focus.domain.model.FocusTimer
import com.mvp.feature.focus.domain.repository.FocusRepository
import com.mvp.feature.focus.domain.usecase.ObserveFocusTimerUseCase
import com.mvp.feature.focus.domain.usecase.ObserveFocusUseCase
import com.mvp.feature.focus.domain.usecase.PauseFocusUseCase
import com.mvp.feature.focus.domain.usecase.ResumeFocusUseCase
import com.mvp.feature.focus.domain.usecase.StartFocus
import com.mvp.feature.focus.domain.usecase.StartFocusUseCase
import com.mvp.feature.focus.domain.usecase.StartNextFocusPhaseUseCase
import com.mvp.feature.focus.domain.usecase.StopFocusUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FocusUseCasesTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    // Horloge du test : celle du planificateur de coroutines, décalée, pour que delay() et now() avancent ensemble.
    private val start = 1_000_000_000L
    private val now: () -> Long = { start + scope.testScheduler.currentTime }

    private val storage = InMemorySecureStorage()
    private var repository: FocusRepository = SecureFocusRepository(storage)
    private val scheduler = FakeNotificationScheduler()
    private val notifications = FakeNotificationsPreference()
    private val appLock = FakeAppLockPreference()
    private val session = FakeSessionExpiry(expiresAt = start + DAY)
    private val durations = FocusDurations()
    private val alarm = NotificationFocusAlarm(scheduler, notifications, durations)

    private val startFocus get() = StartFocusUseCase(repository, alarm, durations, now, dispatcher)
    private val pause get() = PauseFocusUseCase(repository, alarm, now, dispatcher)
    private val resume get() = ResumeFocusUseCase(repository, alarm, now, dispatcher)
    private val next get() = StartNextFocusPhaseUseCase(repository, alarm, durations, now, dispatcher)
    private val stop get() = StopFocusUseCase(repository, alarm, dispatcher)
    private val observe get() = ObserveFocusUseCase(repository, session, appLock, durations, now, dispatcher)
    private val timer get() = ObserveFocusTimerUseCase(observe, now, dispatcher)

    private suspend fun current(): FocusState? = (observe(NoParams).first() as AppResult.Success).data
    private suspend fun currentTimer(): FocusTimer? = (timer(NoParams).first() as AppResult.Success).data
    private fun alarmAt(): Long? = scheduler.scheduled[NotificationFocusAlarm.GROUP]?.single()?.atEpochMillis

    @Test
    fun startsAWorkPhaseOnATaskWithItsEndAlert() = scope.runTest {
        assertNull(current())

        assertEquals(AppResult.Success(Unit), startFocus(StartFocus("t1", "  Écrire le rapport ")))

        val state = current()!!
        assertEquals("t1", state.todoId)
        assertEquals("Écrire le rapport", state.title)
        assertEquals("Écrire le rapport", state.lockScreenTitle)
        assertEquals(FocusPhase.Work, state.phase)
        assertEquals(FocusStatus.Running, state.status)
        assertEquals(now() + 25 * MINUTE, state.endsAt)
        assertEquals(now(), state.phaseStartedAt)
        assertEquals(25 * MINUTE, state.remainingMillis)
        assertEquals(FocusPhase.ShortBreak, state.nextPhase)
        assertEquals("mvpmodular://tasks/t1", state.link)
        val alert = scheduler.scheduled.getValue("focus").single()
        assertEquals(now() + 25 * MINUTE, alert.atEpochMillis)
        assertEquals("Concentration terminée", alert.title)
        assertEquals("Place à la pause courte (5 min).", alert.body)
        assertEquals("mvpmodular://tasks/t1", alert.deepLink)
        // Écran verrouillé : jamais le titre de la tâche dans l'alerte.
        assertTrue("rapport" !in alert.title + alert.body)
    }

    @Test
    fun rejectsATaskWithoutIdOrTitle() = scope.runTest {
        assertIs<AppError.Validation>((startFocus(StartFocus("t1", "   ")) as AppResult.Failure).error)
        assertIs<AppError.Validation>((startFocus(StartFocus(" ", "Titre")) as AppResult.Failure).error)
        assertNull(current())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun cutsAVeryLongTitle() = scope.runTest {
        startFocus(StartFocus("t1", "a".repeat(200)))

        assertEquals(StartFocusUseCase.MAX_TITLE_LENGTH, current()!!.title.length)
    }

    @Test
    fun startingAnotherTaskReplacesTheSession() = scope.runTest {
        startFocus(StartFocus("t1", "Première"))
        advanceTimeBy(10 * MINUTE)

        startFocus(StartFocus("t2", "Seconde"))

        val state = current()!!
        assertEquals("t2", state.todoId)
        assertEquals(25 * MINUTE, state.remainingMillis)
        assertEquals(now() + 25 * MINUTE, alarmAt())
    }

    @Test
    fun pauseKeepsTheRemainingTimeAndResumeContinuesFromThere() = scope.runTest {
        startFocus(StartFocus("t1", "Rapport"))
        advanceTimeBy(10 * MINUTE)

        assertEquals(AppResult.Success(Unit), pause(NoParams))
        val paused = current()!!
        assertEquals(FocusStatus.Paused, paused.status)
        assertEquals(15 * MINUTE, paused.remainingMillis)
        assertNull(paused.endsAt)
        assertNull(paused.phaseStartedAt)
        assertNull(scheduler.scheduled["focus"])

        // Le temps passé en pause ne compte pas.
        advanceTimeBy(60 * MINUTE)
        assertEquals(15 * MINUTE, current()!!.remainingMillis)

        assertEquals(AppResult.Success(Unit), resume(NoParams))
        val resumed = current()!!
        assertEquals(FocusStatus.Running, resumed.status)
        assertEquals(now() + 15 * MINUTE, resumed.endsAt)
        // Début virtuel de la phase : la barre de progression reprend aux 10 minutes déjà faites.
        assertEquals(now() - 10 * MINUTE, resumed.phaseStartedAt)
        assertEquals(now() + 15 * MINUTE, alarmAt())
    }

    @Test
    fun pauseOrResumeWithoutSessionFails() = scope.runTest {
        assertEquals(AppResult.Failure(AppError.NotFound), pause(NoParams))
        assertEquals(AppResult.Failure(AppError.NotFound), resume(NoParams))
        assertEquals(AppResult.Failure(AppError.NotFound), next(NoParams))
    }

    @Test
    fun aFinishedPhaseCannotBePaused() = scope.runTest {
        startFocus(StartFocus("t1", "Rapport"))
        advanceTimeBy(25 * MINUTE)

        assertIs<AppError.Validation>((pause(NoParams) as AppResult.Failure).error)
        assertEquals(FocusStatus.Finished, current()!!.status)
    }

    @Test
    fun theStateTurnsFinishedAtTheEndOfThePhase() = scope.runTest {
        startFocus(StartFocus("t1", "Rapport"))
        val states = mutableListOf<FocusState?>()
        val job = launch { observe(NoParams).collect { states += (it as AppResult.Success).data } }
        runCurrent()

        advanceTimeBy(25 * MINUTE - 1)
        runCurrent()
        assertEquals(listOf(FocusStatus.Running), states.map { it?.status })

        advanceTimeBy(1)
        runCurrent()
        val finished = states.last()!!
        assertEquals(FocusStatus.Finished, finished.status)
        assertEquals(0, finished.remainingMillis)
        assertNull(finished.endsAt)
        // Pas encore compté : il l'est en passant à la pause.
        assertEquals(0, finished.completedWorkPhases)
        assertEquals(FocusPhase.ShortBreak, finished.nextPhase)
        job.cancel()
    }

    @Test
    fun aLongBreakComesAfterFourCompletedWorkPhases() = scope.runTest {
        startFocus(StartFocus("t1", "Rapport"))
        repeat(3) { round ->
            advanceTimeBy(25 * MINUTE)
            next(NoParams)
            val pauseState = current()!!
            assertEquals(FocusPhase.ShortBreak, pauseState.phase)
            assertEquals(round + 1, pauseState.completedWorkPhases)
            assertEquals(5 * MINUTE, pauseState.remainingMillis)
            assertEquals(now() + 5 * MINUTE, alarmAt())
            assertEquals("Pause terminée", scheduler.scheduled.getValue("focus").single().title)
            advanceTimeBy(5 * MINUTE)
            next(NoParams)
            assertEquals(FocusPhase.Work, current()!!.phase)
        }
        advanceTimeBy(25 * MINUTE)
        assertEquals(FocusPhase.LongBreak, current()!!.nextPhase)

        next(NoParams)

        val longBreak = current()!!
        assertEquals(FocusPhase.LongBreak, longBreak.phase)
        assertEquals(4, longBreak.completedWorkPhases)
        assertEquals(15 * MINUTE, longBreak.remainingMillis)
    }

    @Test
    fun theFourthWorkPhaseAlertAnnouncesTheLongBreak() = scope.runTest {
        startFocus(StartFocus("t1", "Rapport"))
        repeat(3) {
            advanceTimeBy(25 * MINUTE)
            next(NoParams)
            next(NoParams)
        }

        assertEquals("Place à la pause longue (15 min).", scheduler.scheduled.getValue("focus").single().body)
    }

    @Test
    fun skippingAWorkPhaseDoesNotCountIt() = scope.runTest {
        startFocus(StartFocus("t1", "Rapport"))
        advanceTimeBy(10 * MINUTE)
        pause(NoParams)

        next(NoParams)

        val state = current()!!
        assertEquals(FocusPhase.ShortBreak, state.phase)
        assertEquals(0, state.completedWorkPhases)
        assertEquals(FocusStatus.Running, state.status)
    }

    @Test
    fun stopEndsTheSessionAndItsAlert() = scope.runTest {
        startFocus(StartFocus("t1", "Rapport"))

        assertEquals(AppResult.Success(Unit), stop(NoParams))

        assertNull(current())
        assertNull(scheduler.scheduled["focus"])
        assertTrue(storage.values.isEmpty())
        // Sans session : rien à faire, pas d'erreur.
        assertEquals(AppResult.Success(Unit), stop(NoParams))
    }

    @Test
    fun noAlertWhenNotificationsAreOff() = scope.runTest {
        notifications.enabled.value = false

        startFocus(StartFocus("t1", "Rapport"))

        assertEquals(FocusStatus.Running, current()!!.status)
        assertNull(scheduler.scheduled["focus"])
    }

    @Test
    fun theAppLockHidesTheTitleOutsideTheApp() = scope.runTest {
        appLock.enabled.value = true

        startFocus(StartFocus("t1", "Rapport"))

        val state = current()!!
        assertEquals("Rapport", state.title)
        assertNull(state.lockScreenTitle)
    }

    @Test
    fun nothingIsShownWithoutAValidSessionAndLogoutClearsIt() = scope.runTest {
        startFocus(StartFocus("t1", "Rapport"))
        session.expiresAt.value = null
        assertNull(current())

        session.expiresAt.value = start + DAY
        assertEquals("t1", current()?.todoId)

        FocusUserSessionObserver(repository, alarm).clearUserData()

        assertNull(current())
        assertNull(scheduler.scheduled["focus"])
        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun theSessionDisappearsWhenTheLoginSessionExpires() = scope.runTest {
        session.expiresAt.value = start + 10 * MINUTE
        startFocus(StartFocus("t1", "Rapport"))
        val states = mutableListOf<FocusState?>()
        val job = launch { observe(NoParams).collect { states += (it as AppResult.Success).data } }
        runCurrent()

        advanceTimeBy(10 * MINUTE)
        runCurrent()

        assertNull(states.last())
        job.cancel()
    }

    @Test
    fun theSessionSurvivesARestart() = scope.runTest {
        startFocus(StartFocus("t1", "Rapport"))
        advanceTimeBy(5 * MINUTE)
        pause(NoParams)

        repository = SecureFocusRepository(storage)

        val state = current()!!
        assertEquals("Rapport", state.title)
        assertEquals(FocusStatus.Paused, state.status)
        assertEquals(20 * MINUTE, state.remainingMillis)
    }

    @Test
    fun anUnreadableStoredSessionCountsAsNone() = scope.runTest {
        storage.values["focus.session"] = "{pas du json"
        assertNull(current())

        // Ni en cours ni en pause : incohérent.
        storage.values["focus.session"] = """{"todo":"t1","title":"R","phase":"Work","dur":1500000,"done":0}"""
        repository = SecureFocusRepository(storage)
        assertNull(current())

        storage.values["focus.session"] = """{"todo":"t1","title":"R","phase":"Nap","dur":1500000,"end":1,"done":0}"""
        repository = SecureFocusRepository(storage)
        assertNull(current())
    }

    @Test
    fun theTimerTicksEverySecondWithALabel() = scope.runTest {
        startFocus(StartFocus("t1", "Rapport"))
        val timers = mutableListOf<FocusTimer?>()
        val job = launch { timer(NoParams).collect { timers += (it as AppResult.Success).data } }
        runCurrent()
        assertEquals("25:00", timers.last()!!.remainingLabel)
        assertEquals(0f, timers.last()!!.progress)

        advanceTimeBy(1)
        runCurrent()
        assertEquals("25:00", timers.last()!!.remainingLabel)
        advanceTimeBy(999)
        runCurrent()
        assertEquals("24:59", timers.last()!!.remainingLabel)

        advanceTimeBy(25 * MINUTE / 2 - 1000)
        runCurrent()
        assertEquals("12:30", timers.last()!!.remainingLabel)
        assertEquals(0.5f, timers.last()!!.progress)

        advanceTimeBy(25 * MINUTE / 2)
        runCurrent()
        val last = timers.last()!!
        assertEquals("00:00", last.remainingLabel)
        assertEquals(1f, last.progress)
        assertEquals(FocusStatus.Finished, last.state.status)
        job.cancel()
    }

    @Test
    fun aPausedTimerDoesNotTick() = scope.runTest {
        startFocus(StartFocus("t1", "Rapport"))
        advanceTimeBy(90_000)
        pause(NoParams)

        assertEquals("23:30", currentTimer()!!.remainingLabel)
        advanceTimeBy(10 * MINUTE)
        assertEquals("23:30", currentTimer()!!.remainingLabel)
    }

    @Test
    fun formatsRemainingTime() {
        assertEquals("25:00", FocusFormat.remaining(25 * MINUTE))
        assertEquals("00:01", FocusFormat.remaining(1))
        assertEquals("00:00", FocusFormat.remaining(0))
        assertEquals("00:00", FocusFormat.remaining(-5))
        assertEquals("100:00", FocusFormat.remaining(100 * MINUTE))
    }

    private companion object {
        const val MINUTE = 60_000L
        const val DAY = 24 * 60 * MINUTE
    }
}
