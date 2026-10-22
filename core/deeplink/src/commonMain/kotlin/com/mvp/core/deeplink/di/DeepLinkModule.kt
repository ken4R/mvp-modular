package com.mvp.core.deeplink.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.core.deeplink.data.DeepLinkUserSessionObserver
import com.mvp.core.deeplink.data.InMemoryPendingDeepLinkRepository
import com.mvp.core.deeplink.domain.repository.PendingDeepLinkRepository
import com.mvp.core.deeplink.domain.usecase.ConsumeDeepLinkUseCase
import com.mvp.core.deeplink.domain.usecase.ObservePendingDeepLinkUseCase
import com.mvp.core.deeplink.domain.usecase.OpenDeepLinkUseCase
import org.koin.dsl.bind
import org.koin.dsl.module

val deepLinkModule = module {
    // Data
    single<PendingDeepLinkRepository> { InMemoryPendingDeepLinkRepository() }
    // Prévenu par feature:auth : le lien en attente est oublié à la déconnexion (pas à la connexion).
    single { DeepLinkUserSessionObserver(get()) } bind UserSessionObserver::class

    // Domain
    factory { OpenDeepLinkUseCase(get(), get<AppDispatchers>().default) }
    factory { ObservePendingDeepLinkUseCase(get(), get<AppDispatchers>().default) }
    factory { ConsumeDeepLinkUseCase(get(), get<AppDispatchers>().default) }
}
