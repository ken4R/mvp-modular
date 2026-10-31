package com.mvp.feature.focus.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.focus.domain.repository.FocusAlarm
import com.mvp.feature.focus.domain.repository.FocusRepository
import kotlinx.coroutines.CoroutineDispatcher

/** Reprend une phase en pause là où elle s'était arrêtée, et reprogramme l'alerte de fin. */
class ResumeFocusUseCase(
    private val repository: FocusRepository,
    private val alarm: FocusAlarm,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Unit>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Unit> {
        var resumed = false
        val session = repository.update { current ->
            if (current == null || !current.isPaused) return@update current
            resumed = true
            current.copy(endsAt = now() + (current.pausedRemainingMillis ?: 0), pausedRemainingMillis = null)
        } ?: return AppResult.Failure(AppError.NotFound)
        if (resumed) alarm.schedule(session)
        return AppResult.Success(Unit)
    }
}
