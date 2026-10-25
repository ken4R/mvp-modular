package com.mvp.core.featureflags.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.featureflags.domain.model.FeatureFlag
import com.mvp.core.featureflags.domain.model.FeatureFlagResolver
import com.mvp.core.featureflags.domain.repository.FeatureFlagRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** `true` si la feature doit être affichée ; réémis à chaque changement (distant ou menu debug). */
class ObserveFeatureFlagUseCase(
    private val repository: FeatureFlagRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<FeatureFlag, Boolean>(dispatcher) {

    override fun execute(params: FeatureFlag): Flow<AppResult<Boolean>> =
        repository.observeInputs()
            .map { FeatureFlagResolver.resolve(params, it, repository.isDevBuild).enabled }
            .distinctUntilChanged()
            .map { AppResult.Success(it) }
}
