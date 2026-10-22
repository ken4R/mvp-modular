package com.mvp.core.deeplink.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.core.deeplink.domain.DeepLinks
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.core.deeplink.domain.repository.PendingDeepLinkRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Un lien vient d'ouvrir l'app (lien externe ou tap sur un rappel) : sa destination est mise en attente,
 * et l'app y va dès qu'elle peut l'afficher. Règle métier : un lien non reconnu mène à l'écran par défaut
 * (onglet Tâches), et renvoie [AppError.Validation].
 */
class OpenDeepLinkUseCase(
    private val pending: PendingDeepLinkRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<String, DeepLink>(dispatcher) {

    override suspend fun execute(params: String): AppResult<DeepLink> {
        val link = DeepLinks.parse(params)
        pending.set(link ?: DeepLink.Tasks())
        return if (link != null) AppResult.Success(link) else AppResult.Failure(AppError.Validation("Lien non reconnu"))
    }
}
