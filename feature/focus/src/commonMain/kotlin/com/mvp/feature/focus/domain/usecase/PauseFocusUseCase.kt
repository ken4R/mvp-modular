package com.mvp.feature.focus.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.focus.domain.repository.FocusAlarm
import com.mvp.feature.focus.domain.repository.FocusRepository
import kotlinx.coroutines.CoroutineDispatcher

/** Met la phase en cours en pause : le temps restant est gardé, l'alerte de fin annulée. Sans effet sur une phase finie. */
class PauseFocusUseCase(
    private val repository: FocusRepository,
    private val alarm: FocusAlarm,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Unit>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Unit> {
        var paused = false
        val session = repository.update { current ->
            val time = now()
            if (current == null || current.isPaused || current.isFinishedAt(time)) return@update current
            paused = true
            current.copy(endsAt = null, pausedRemainingMillis = current.remainingAt(time))
        } ?: return AppResult.Failure(AppError.NotFound)
        if (paused) alarm.cancel()
        return if (session.isPaused) AppResult.Success(Unit) else AppResult.Failure(AppError.Validation("La phase est terminée"))
    }
}
