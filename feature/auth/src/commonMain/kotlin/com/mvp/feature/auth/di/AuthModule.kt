package com.mvp.feature.auth.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.feature.auth.data.repository.AuthRepositoryImpl
import com.mvp.feature.auth.data.source.AuthRemoteDataSource
import com.mvp.feature.auth.data.source.FakeAuthRemoteDataSource
import com.mvp.feature.auth.data.source.SecureSessionLocalDataSource
import com.mvp.feature.auth.data.source.SessionLocalDataSource
import com.mvp.feature.auth.domain.repository.AuthRepository
import com.mvp.feature.auth.domain.usecase.LoginUseCase
import com.mvp.feature.auth.domain.usecase.LogoutUseCase
import com.mvp.feature.auth.domain.usecase.ObserveSessionUseCase
import org.koin.dsl.module

val authModule = module {
    // Data : pas encore de backend, le faux service tourne sur l'appareil.
    single<AuthRemoteDataSource> { FakeAuthRemoteDataSource() }
    single<SessionLocalDataSource> { SecureSessionLocalDataSource(get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }

    // Domain : les features à prévenir sont toutes celles qui déclarent un UserSessionObserver.
    factory { ObserveSessionUseCase(get(), get<AppDispatchers>().io) }
    factory { LoginUseCase(get(), getAll(), get<AppDispatchers>().io) }
    factory { LogoutUseCase(get(), getAll(), get<AppDispatchers>().io) }
}
