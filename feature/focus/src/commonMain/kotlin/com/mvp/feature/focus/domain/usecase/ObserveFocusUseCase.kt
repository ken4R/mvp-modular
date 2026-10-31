package com.mvp.feature.focus.domain.usecase

import com.mvp.core.common.domain.applock.AppLockPreference
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.SessionExpiry
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.deeplink.domain.DeepLinks
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.feature.focus.domain.model.FocusDurations
import com.mvp.feature.focus.domain.model.FocusSession
import com.mvp.feature.focus.domain.model.FocusSnapshot
import com.mvp.feature.focus.domain.model.FocusStatus
import com.mvp.feature.focus.domain.repository.FocusRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/**
 * Session de concentration en cours (`null` sans session), réémise à chaque changement, au changement du verrou et
 * une fois à la fin de la phase (passage à [FocusStatus.Finished]). Pas de tic par seconde : la notification Android
 * et la Live Activity iOS comptent elles-mêmes jusqu'à `endsAt`. L'écran utilise [ObserveFocusTimerUseCase].
 * Sans session de connexion valide, rien n'est montré (la déconnexion efface aussi la session).
 */
class ObserveFocusUseCase(
    private val repository: FocusRepository,
    private val session: SessionExpiry,
    private val appLock: AppLockPreference,
    private val durations: FocusDurations,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, FocusSnapshot?>(dispatcher) {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun execute(params: NoParams): Flow<AppResult<FocusSnapshot?>> =
        combine(repository.observe(), session.observeExpiresAt(), appLock.observeAppLockEnabled()) { focus, expiresAt, locked ->
            Triple(focus, expiresAt, locked)
        }
            // Rien ne réémet quand la phase se termine ou que la session de connexion expire : on recalcule à l'échéance.
            .transformLatest { (focus, expiresAt, locked) ->
                while (true) {
                    val current = now()
                    if (focus == null || expiresAt == null || expiresAt <= current) {
                        emit(null)
                        break
                    }
                    val state = stateOf(focus, current, locked, durations, visibleUntil = expiresAt)
                    emit(state)
                    val wakeAt = listOfNotNull(focus.endsAt.takeIf { state.isRunning }, expiresAt).min()
                    delay(wakeAt - current)
                }
            }
            .distinctUntilChanged()
            .map { AppResult.Success(it) }

    companion object {
        internal fun stateOf(
            focus: FocusSession,
            now: Long,
            appLocked: Boolean,
            durations: FocusDurations,
            visibleUntil: Long,
        ): FocusSnapshot {
            val status = when {
                focus.isPaused -> FocusStatus.Paused
                focus.isFinishedAt(now) -> FocusStatus.Finished
                else -> FocusStatus.Running
            }
            val running = status == FocusStatus.Running
            // Le compte n'avance qu'en passant à la phase suivante : une concentration finie compte déjà pour la suivante.
            val countedWork = focus.completedWorkPhases + if (status == FocusStatus.Finished && !focus.phase.isBreak) 1 else 0
            return FocusSnapshot(
                todoId = focus.todoId,
                title = focus.title,
                lockScreenTitle = focus.title.takeUnless { appLocked },
                phase = focus.phase,
                status = status,
                endsAt = focus.endsAt.takeIf { running },
                phaseStartedAt = focus.endsAt?.minus(focus.phaseDurationMillis)?.takeIf { running },
                remainingMillis = if (status == FocusStatus.Finished) 0 else focus.remainingAt(now),
                phaseDurationMillis = focus.phaseDurationMillis,
                completedWorkPhases = focus.completedWorkPhases,
                nextPhase = durations.next(focus.phase, countedWork),
                link = DeepLinks.url(DeepLink.Tasks(focus.todoId)),
                visibleUntil = visibleUntil,
            )
        }
    }
}
