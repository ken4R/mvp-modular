package com.mvp.core.featureflags.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.featureflags.data.FeatureFlagRepositoryImpl
import com.mvp.core.featureflags.data.FeatureFlagsConfig
import com.mvp.core.featureflags.data.platform.isDevBuild
import com.mvp.core.featureflags.data.remote.HostedJsonFlagSource
import com.mvp.core.featureflags.data.remote.RemoteFlagSource
import com.mvp.core.featureflags.data.store.FlagStore
import com.mvp.core.featureflags.domain.repository.FeatureFlagRepository
import com.mvp.core.featureflags.domain.usecase.ClearFeatureFlagOverridesUseCase
import com.mvp.core.featureflags.domain.usecase.ObserveFeatureFlagUseCase
import com.mvp.core.featureflags.domain.usecase.ObserveFeatureFlagsUseCase
import com.mvp.core.featureflags.domain.usecase.RefreshRemoteFlagsUseCase
import com.mvp.core.featureflags.domain.usecase.SetFeatureFlagOverrideUseCase
import com.mvp.core.files.PrivateFileStoreFactory
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private val systemNow: () -> Long = { Clock.System.now().toEpochMilliseconds() }

/** Nécessite le `HttpClient` de `networkModule` et la `PrivateFileStoreFactory` de `filesModule`. */
val featureFlagsModule = module {
    // Data. Une autre source distante (Firebase, backend) remplacera HostedJsonFlagSource ici (ADR 0021).
    single { HostedJsonFlagSource(get(), FeatureFlagsConfig.REMOTE_URL) } bind RemoteFlagSource::class
    single {
        FeatureFlagRepositoryImpl(
            store = FlagStore(get<PrivateFileStoreFactory>().create("feature_flags")),
            remote = get(),
            isDevBuild = isDevBuild(),
            now = systemNow,
        )
    } bind FeatureFlagRepository::class

    // Domain : cache sur disque et réseau, d'où le dispatcher d'entrées / sorties.
    factory { ObserveFeatureFlagUseCase(get(), get<AppDispatchers>().io) }
    factory { ObserveFeatureFlagsUseCase(get(), get<AppDispatchers>().io) }
    factory { SetFeatureFlagOverrideUseCase(get(), get<AppDispatchers>().io) }
    factory { ClearFeatureFlagOverridesUseCase(get(), get<AppDispatchers>().io) }
    factory { RefreshRemoteFlagsUseCase(get(), systemNow, get<AppDispatchers>().io) }
}
