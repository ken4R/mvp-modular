package com.mvp.core.crashreporting.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.crashreporting.domain.model.CrashReport
import com.mvp.core.crashreporting.domain.repository.CrashReportRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Journal des rapports « envoyés », affiché dans Réglages des builds de debug. */
class ObserveCrashJournalUseCase(
    private val repository: CrashReportRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, List<CrashReport>>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<List<CrashReport>>> =
        repository.observeJournal().map { AppResult.Success(it) }
}
