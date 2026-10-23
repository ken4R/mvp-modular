package com.mvp.feature.auth.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.common.domain.session.SessionExpiry
import com.mvp.feature.auth.data.repository.AuthRepositoryImpl
import com.mvp.feature.auth.data.session.AuthSessionExpiry
import com.mvp.feature.auth.data.source.AuthRemoteDataSource
import com.mvp.feature.auth.data.source.FakeAuthRemoteDataSource
import com.mvp.feature.auth.data.source.SecureSessionLocalDataSource
import com.mvp.feature.auth.data.source.SessionLocalDataSource
import com.mvp.feature.auth.data.source.UnavailableAuthRemoteDataSource
import com.mvp.feature.auth.data.source.isDemoAuthEnabled
import com.mvp.feature.auth.domain.repository.AuthRepository
import com.mvp.feature.auth.domain.usecase.LoginUseCase
import com.mvp.feature.auth.domain.usecase.LogoutUseCase
import com.mvp.feature.auth.domain.usecase.ObserveSessionUseCase
import org.koin.dsl.module

val authModule = module {
    // Data : pas encore de backend. Le faux service ne tourne que dans les builds de développement ;
    // un build distribué refuse toute connexion tant qu'aucun vrai service n'est branché.
    // Appel direct (pas de variable capturée) : sur Android, R8 replie la constante et retire le faux service.
    single<AuthRemoteDataSource> {
        if (isDemoAuthEnabled()) FakeAuthRemoteDataSource() else UnavailableAuthRemoteDataSource()
    }
    single<SessionLocalDataSource> { SecureSessionLocalDataSource(get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), acceptsDemoSessions = isDemoAuthEnabled()) }
    // Lu par feature:todo : aucun rappel ne part hors d'une session valide.
    single<SessionExpiry> { AuthSessionExpiry(get()) }

    // Domain : les features à prévenir sont toutes celles qui déclarent un UserSessionObserver.
    factory { ObserveSessionUseCase(get(), get<AppDispatchers>().io) }
    factory { LoginUseCase(get(), getAll(), get<AppDispatchers>().io) }
    factory { LogoutUseCase(get(), getAll(), get<AppDispatchers>().io) }
}
