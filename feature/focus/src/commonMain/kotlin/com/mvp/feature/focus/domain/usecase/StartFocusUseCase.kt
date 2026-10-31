package com.mvp.feature.focus.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.focus.domain.model.FocusDurations
import com.mvp.feature.focus.domain.model.FocusPhase
import com.mvp.feature.focus.domain.model.FocusSession
import com.mvp.feature.focus.domain.repository.FocusAlarm
import com.mvp.feature.focus.domain.repository.FocusRepository
import kotlinx.coroutines.CoroutineDispatcher

/** Tâche sur laquelle se concentrer : l'app passe son id et son titre (feature:focus ne lit pas les tâches). */
data class StartFocus(val todoId: String, val title: String)

/**
 * Lance une concentration de 25 min sur une tâche, à la place de la session en cours s'il y en a une
 * (une seule session à la fois). L'alerte de fin est programmée.
 */
class StartFocusUseCase(
    private val repository: FocusRepository,
    private val alarm: FocusAlarm,
    private val durations: FocusDurations,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : UseCase<StartFocus, Unit>(dispatcher) {

    override suspend fun execute(params: StartFocus): AppResult<Unit> {
        val todoId = params.todoId.trim()
        val title = params.title.trim()
        if (todoId.isEmpty() || todoId.length > MAX_ID_LENGTH) return AppResult.Failure(AppError.Validation("Tâche inconnue"))
        if (title.isEmpty()) return AppResult.Failure(AppError.Validation("La tâche n'a pas de titre"))
        val duration = durations.of(FocusPhase.Work)
        val session = FocusSession(
            todoId = todoId,
            title = title.take(MAX_TITLE_LENGTH),
            phase = FocusPhase.Work,
            phaseDurationMillis = duration,
            endsAt = now() + duration,
            pausedRemainingMillis = null,
            completedWorkPhases = 0,
        )
        repository.update { session }
        alarm.schedule(session)
        return AppResult.Success(Unit)
    }

    companion object {
        /** Comme un titre de tâche (AddTodoUseCase) : un titre plus long est coupé. */
        const val MAX_TITLE_LENGTH = 80
        internal const val MAX_ID_LENGTH = 64
    }
}
