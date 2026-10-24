package com.mvp.feature.onboarding.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.onboarding.domain.model.ONBOARDING_VERSION
import com.mvp.feature.onboarding.domain.repository.OnboardingRepository
import kotlinx.coroutines.CoroutineDispatcher

/** Présentation terminée ou passée : elle n'est plus montrée sur cet appareil, même après une déconnexion. */
class CompleteOnboardingUseCase(
    private val repository: OnboardingRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Unit>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Unit> = repository.setCompletedVersion(ONBOARDING_VERSION)
}
