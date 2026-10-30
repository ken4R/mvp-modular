package com.mvp.core.deeplink.ios

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.ios.Subscription
import com.mvp.core.common.ios.SwiftUseCases
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.core.deeplink.domain.usecase.ConsumeDeepLinkUseCase
import com.mvp.core.deeplink.domain.usecase.ObservePendingDeepLinkUseCase
import com.mvp.core.deeplink.domain.usecase.OpenDeepLinkUseCase
import org.koin.core.component.get

/**
 * Point d'accès Swift aux use cases des liens profonds.
 * Koin n'est pas utilisable directement depuis Swift. Les use cases suspend s'appellent en `async` côté Swift ;
 * les Flow passent par des callbacks (voir SwiftUseCases).
 */
object DeepLinkUseCases : SwiftUseCases() {
    fun openDeepLink(): OpenDeepLinkUseCase = get()
    fun consumeDeepLink(): ConsumeDeepLinkUseCase = get()

    /** Lien profond en attente d'affichage, ou `null`. Les callbacks sont appelés sur le main thread. */
    fun observePendingDeepLink(onLink: (DeepLink?) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObservePendingDeepLinkUseCase>()(NoParams).subscribe(onLink, onError)
}
