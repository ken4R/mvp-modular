package com.mvp.feature.onboarding.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.onboarding.domain.model.ONBOARDING_VERSION
import com.mvp.feature.onboarding.domain.repository.OnboardingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * `true` tant que la présentation actuelle n'a été ni terminée ni passée sur cet appareil : l'app l'affiche
 * avant les onglets. Ne réémet que si la réponse change.
 */
class ObserveOnboardingRequiredUseCase(
    private val repository: OnboardingRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, Boolean>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<Boolean>> =
        repository.observeCompletedVersion()
            .map { completed -> completed == null || completed < ONBOARDING_VERSION }
            .distinctUntilChanged()
            .map { AppResult.Success(it) }
}
