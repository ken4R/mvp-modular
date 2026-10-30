package com.mvp.feature.onboarding.ios

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.ios.Subscription
import com.mvp.core.common.ios.SwiftUseCases
import com.mvp.feature.onboarding.domain.usecase.CompleteOnboardingUseCase
import com.mvp.feature.onboarding.domain.usecase.GetOnboardingStepsUseCase
import com.mvp.feature.onboarding.domain.usecase.ObserveOnboardingRequiredUseCase
import com.mvp.feature.onboarding.domain.usecase.RestartOnboardingUseCase
import org.koin.core.component.get

/**
 * Point d'accès Swift aux use cases de la présentation du premier lancement.
 * Koin n'est pas utilisable directement depuis Swift. Les use cases suspend s'appellent en `async` côté Swift ;
 * les Flow passent par des callbacks (voir SwiftUseCases).
 */
object OnboardingUseCases : SwiftUseCases() {
    fun getOnboardingSteps(): GetOnboardingStepsUseCase = get()
    fun completeOnboarding(): CompleteOnboardingUseCase = get()
    fun restartOnboarding(): RestartOnboardingUseCase = get()

    /** `true` : afficher la présentation avant les onglets (ADR 0016). Les callbacks sont appelés sur le main thread. */
    fun observeOnboardingRequired(onRequired: (Boolean) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveOnboardingRequiredUseCase>()(NoParams).subscribe(onRequired, onError)
}
