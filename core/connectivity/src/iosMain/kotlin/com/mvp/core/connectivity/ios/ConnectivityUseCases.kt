package com.mvp.core.connectivity.ios

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.ios.Subscription
import com.mvp.core.common.ios.SwiftUseCases
import com.mvp.core.connectivity.domain.usecase.ObserveConnectivityUseCase
import org.koin.core.component.get

/**
 * Point d'accès Swift aux use cases de la connectivité.
 * Koin n'est pas utilisable directement depuis Swift. Les use cases suspend s'appellent en `async` côté Swift ;
 * les Flow passent par des callbacks (voir SwiftUseCases).
 */
object ConnectivityUseCases : SwiftUseCases() {
    /** `true` tant que l'appareil a accès à internet (bandeau « Hors ligne »). Les callbacks sont appelés sur le main thread. */
    fun observeConnectivity(onOnline: (Boolean) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveConnectivityUseCase>()(NoParams).subscribe(onOnline, onError)
}
