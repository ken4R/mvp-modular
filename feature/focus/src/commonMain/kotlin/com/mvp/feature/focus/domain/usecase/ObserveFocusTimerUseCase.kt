package com.mvp.feature.focus.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.focus.domain.model.FocusFormat
import com.mvp.feature.focus.domain.model.FocusTimer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest

/**
 * Compte à rebours de l'écran Focus : la session de [ObserveFocusUseCase] et son temps restant, réémis à chaque
 * changement de seconde affichée tant que la phase tourne. `null` sans session.
 */
class ObserveFocusTimerUseCase(
    private val observeFocus: ObserveFocusUseCase,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, FocusTimer?>(dispatcher) {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun execute(params: NoParams): Flow<AppResult<FocusTimer?>> =
        observeFocus(NoParams).transformLatest { result ->
            val state = when (result) {
                is AppResult.Failure -> return@transformLatest emit(result)
                is AppResult.Success -> result.data ?: return@transformLatest emit(AppResult.Success(null))
            }
            while (true) {
                val endsAt = state.endsAt
                val remaining = if (endsAt != null) (endsAt - now()).coerceAtLeast(0) else state.remainingMillis
                val elapsed = (state.phaseDurationMillis - remaining).coerceIn(0, state.phaseDurationMillis)
                emit(
                    AppResult.Success(
                        FocusTimer(
                            state = state,
                            remainingMillis = remaining,
                            remainingLabel = FocusFormat.remaining(remaining),
                            progress = if (state.phaseDurationMillis > 0) elapsed.toFloat() / state.phaseDurationMillis else 1f,
                        ),
                    ),
                )
                // Fin de phase : ObserveFocusUseCase réémet (Finished), ce qui relance ce bloc.
                if (endsAt == null || remaining == 0L) break
                // Prochain changement de la seconde affichée (arrondie au-dessus).
                val sub = remaining % 1000
                delay(if (sub == 0L) 1000 else sub)
            }
        }
}
