package com.mvp.core.deeplink.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.core.deeplink.domain.repository.PendingDeepLinkRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Lien en attente, ou `null`. Observé par la navigation de l'app, qui le retire une fois affiché. */
class ObservePendingDeepLinkUseCase(
    private val pending: PendingDeepLinkRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, DeepLink?>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<DeepLink?>> = pending.observe().map { AppResult.Success(it) }
}
