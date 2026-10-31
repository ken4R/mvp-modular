package com.mvp.feature.focus.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.focus.domain.repository.FocusAlarm
import com.mvp.feature.focus.domain.repository.FocusRepository
import kotlinx.coroutines.CoroutineDispatcher

/** Termine la session : plus de notification en cours ni de Live Activity, l'alerte de fin est annulée. */
class StopFocusUseCase(
    private val repository: FocusRepository,
    private val alarm: FocusAlarm,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Unit>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Unit> {
        repository.update { null }
        alarm.cancel()
        return AppResult.Success(Unit)
    }
}
