package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.reminder.LocationReminderScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** `true` si les rappels par lieu peuvent partir (permissions de localisation) ; sinon la fiche l'explique. */
class ObserveLocationRemindersAvailableUseCase(
    private val scheduler: LocationReminderScheduler,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, Boolean>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<Boolean>> =
        scheduler.observeAvailability().map { AppResult.Success(it) }
}

/**
 * Relit les permissions de localisation : à appeler au retour au premier plan et après une demande de permission.
 * Les rappels par lieu sont alors reposés ou retirés par [SyncTodoLocationRemindersUseCase].
 */
class RefreshLocationRemindersAvailabilityUseCase(
    private val scheduler: LocationReminderScheduler,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Unit>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Unit> {
        scheduler.refreshAvailability()
        return AppResult.Success(Unit)
    }
}
