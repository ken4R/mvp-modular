package com.mvp.feature.onboarding.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.onboarding.domain.model.OnboardingDevice
import com.mvp.feature.onboarding.domain.model.OnboardingStep
import kotlinx.coroutines.CoroutineDispatcher

/** Étapes à montrer sur cet appareil, dans l'ordre : le verrou n'est proposé que si l'appareil peut l'utiliser. */
class GetOnboardingStepsUseCase(dispatcher: CoroutineDispatcher) : UseCase<OnboardingDevice, List<OnboardingStep>>(dispatcher) {

    override suspend fun execute(params: OnboardingDevice): AppResult<List<OnboardingStep>> =
        AppResult.Success(OnboardingStep.entries.filter { it != OnboardingStep.AppLock || params.appLockAvailable })
}
