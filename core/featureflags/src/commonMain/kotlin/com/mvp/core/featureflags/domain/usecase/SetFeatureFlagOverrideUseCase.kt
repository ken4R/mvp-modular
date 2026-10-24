package com.mvp.core.featureflags.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.core.featureflags.domain.model.FeatureFlag
import com.mvp.core.featureflags.domain.repository.FeatureFlagRepository
import kotlinx.coroutines.CoroutineDispatcher

internal val releaseBuildError = AppError.Validation("Réglage réservé aux builds de debug")

/** Menu debug : force un flag allumé ou éteint ; `enabled = null` revient à la valeur normale. Refusé en release. */
class SetFeatureFlagOverrideUseCase(
    private val repository: FeatureFlagRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<SetFeatureFlagOverrideUseCase.Params, Unit>(dispatcher) {

    data class Params(val flag: FeatureFlag, val enabled: Boolean?)

    override suspend fun execute(params: Params): AppResult<Unit> {
        if (!repository.isDevBuild) return AppResult.Failure(releaseBuildError)
        repository.setOverride(params.flag.key, params.enabled)
        return AppResult.Success(Unit)
    }
}

/** Menu debug : retire tous les réglages locaux. Refusé en release. */
class ClearFeatureFlagOverridesUseCase(
    private val repository: FeatureFlagRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Unit>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Unit> {
        if (!repository.isDevBuild) return AppResult.Failure(releaseBuildError)
        repository.clearOverrides()
        return AppResult.Success(Unit)
    }
}
