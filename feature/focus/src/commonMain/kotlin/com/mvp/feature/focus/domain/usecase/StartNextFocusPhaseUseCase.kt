package com.mvp.feature.focus.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.focus.domain.model.FocusDurations
import com.mvp.feature.focus.domain.repository.FocusAlarm
import com.mvp.feature.focus.domain.repository.FocusRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Lance la phase suivante, tout de suite : pause après une concentration (longue toutes les 4), concentration après
 * une pause. Appelé à la fin d'une phase (« Commencer la pause ») ou pour l'écourter (« Passer »). Une concentration
 * écourtée ne compte pas.
 */
class StartNextFocusPhaseUseCase(
    private val repository: FocusRepository,
    private val alarm: FocusAlarm,
    private val durations: FocusDurations,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Unit>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Unit> {
        val session = repository.update { current ->
            current ?: return@update null
            val time = now()
            val completed = current.completedWorkPhases +
                if (!current.phase.isBreak && current.isFinishedAt(time)) 1 else 0
            val next = durations.next(current.phase, completed)
            val duration = durations.of(next)
            current.copy(
                phase = next,
                phaseDurationMillis = duration,
                endsAt = time + duration,
                pausedRemainingMillis = null,
                completedWorkPhases = completed,
            )
        } ?: return AppResult.Failure(AppError.NotFound)
        alarm.schedule(session)
        return AppResult.Success(Unit)
    }
}
