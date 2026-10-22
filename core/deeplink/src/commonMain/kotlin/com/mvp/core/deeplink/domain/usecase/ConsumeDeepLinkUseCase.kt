package com.mvp.core.deeplink.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.core.deeplink.domain.repository.PendingDeepLinkRepository
import kotlinx.coroutines.CoroutineDispatcher

/** L'app a affiché [DeepLink] : il n'est plus en attente (un lien plus récent, lui, reste en attente). */
class ConsumeDeepLinkUseCase(
    private val pending: PendingDeepLinkRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<DeepLink, Unit>(dispatcher) {

    override suspend fun execute(params: DeepLink): AppResult<Unit> {
        pending.consume(params)
        return AppResult.Success(Unit)
    }
}
