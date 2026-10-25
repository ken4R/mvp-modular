package com.mvp.core.featureflags.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.featureflags.domain.model.FeatureFlagResolver
import com.mvp.core.featureflags.domain.model.FeatureFlagState
import com.mvp.core.featureflags.domain.repository.FeatureFlagRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Tous les flags avec leur valeur et son origine, pour le menu « Feature flags (debug) ». */
class ObserveFeatureFlagsUseCase(
    private val repository: FeatureFlagRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, List<FeatureFlagState>>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<List<FeatureFlagState>>> =
        repository.observeInputs().map { AppResult.Success(FeatureFlagResolver.resolveAll(it, repository.isDevBuild)) }
}
