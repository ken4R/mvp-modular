package com.mvp.feature.auth.ios

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.ios.Subscription
import com.mvp.core.common.ios.SwiftUseCases
import com.mvp.feature.auth.domain.usecase.LoginUseCase
import com.mvp.feature.auth.domain.usecase.LogoutUseCase
import com.mvp.feature.auth.domain.usecase.ObserveSessionUseCase
import org.koin.core.component.get

/**
 * Point d'accès Swift aux use cases de la connexion.
 * Koin n'est pas utilisable directement depuis Swift. Les use cases suspend s'appellent en `async` côté Swift ;
 * les Flow passent par des callbacks (voir SwiftUseCases).
 */
object AuthUseCases : SwiftUseCases() {
    fun login(): LoginUseCase = get()
    fun logout(): LogoutUseCase = get()

    /** Compte connecté, ou `null` (écran de connexion). Les callbacks sont appelés sur le main thread. */
    fun observeSession(onAccount: (UserAccount?) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveSessionUseCase>()(NoParams).subscribe(onAccount, onError)
}
