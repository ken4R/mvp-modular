package com.mvp.core.featureflags.ios

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.ios.Subscription
import com.mvp.core.common.ios.SwiftUseCases
import com.mvp.core.featureflags.domain.model.FeatureFlag
import com.mvp.core.featureflags.domain.model.FeatureFlagState
import com.mvp.core.featureflags.domain.usecase.ClearFeatureFlagOverridesUseCase
import com.mvp.core.featureflags.domain.usecase.ObserveFeatureFlagUseCase
import com.mvp.core.featureflags.domain.usecase.ObserveFeatureFlagsUseCase
import com.mvp.core.featureflags.domain.usecase.RefreshRemoteFlagsUseCase
import com.mvp.core.featureflags.domain.usecase.SetFeatureFlagOverrideUseCase
import org.koin.core.component.get

/**
 * Point d'accès Swift aux use cases des feature flags.
 * Koin n'est pas utilisable directement depuis Swift. Les use cases suspend s'appellent en `async` côté Swift ;
 * les Flow passent par des callbacks (voir SwiftUseCases).
 */
object FeatureFlagsUseCases : SwiftUseCases() {
    fun refreshRemoteFlags(): RefreshRemoteFlagsUseCase = get()
    fun setFeatureFlagOverride(): SetFeatureFlagOverrideUseCase = get()
    fun clearFeatureFlagOverrides(): ClearFeatureFlagOverridesUseCase = get()

    /** `true` si la feature doit être affichée (ADR 0021). Les callbacks sont appelés sur le main thread. */
    fun observeFeatureFlag(flag: FeatureFlag, onEnabled: (Boolean) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveFeatureFlagUseCase>()(flag).subscribe(onEnabled, onError)

    /** Tous les flags pour le menu debug. Les callbacks sont appelés sur le main thread. */
    fun observeFeatureFlags(onFlags: (List<FeatureFlagState>) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveFeatureFlagsUseCase>()(NoParams).subscribe(onFlags, onError)
}
