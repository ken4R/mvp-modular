package com.mvp.core.analytics.domain.usecase

import com.mvp.core.analytics.domain.model.AnalyticsJournal
import com.mvp.core.analytics.domain.repository.AnalyticsRepository
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Journal de mesure, pour vérifier à l'écran ce qui part et ce qui attend (builds de debug). */
class ObserveAnalyticsJournalUseCase(
    private val repository: AnalyticsRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, AnalyticsJournal>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<AnalyticsJournal>> =
        repository.observeJournal().map { AppResult.Success(it) }
}
