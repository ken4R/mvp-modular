package com.mvp.feature.onboarding.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.onboarding.domain.repository.OnboardingRepository
import kotlinx.coroutines.CoroutineDispatcher

/** « Revoir la présentation » (Réglages) : elle s'affiche de nouveau tout de suite, à la place des onglets. */
class RestartOnboardingUseCase(
    private val repository: OnboardingRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Unit>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Unit> = repository.setCompletedVersion(null)
}
