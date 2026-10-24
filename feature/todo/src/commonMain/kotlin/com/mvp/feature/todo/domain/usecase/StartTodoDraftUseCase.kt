package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.core.deeplink.domain.repository.PendingDeepLinkRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Raccourci vocal « Ajouter une tâche » (Siri, Google Assistant ; ADR 0018) : le titre dicté est mis en attente
 * comme [DeepLink.NewTask], et l'onglet Tâches le prérempli dans le champ « Nouvelle tâche » une fois connecté
 * et déverrouillé. Règles métier : rien n'est ajouté sans le bouton « Ajouter » (qui passe par [AddTodoUseCase]) ;
 * le titre tient sur une ligne, sans caractère de contrôle, coupé à [AddTodoUseCase.MAX_TITLE_LENGTH] caractères ;
 * un titre vide ouvre le champ vide.
 */
class StartTodoDraftUseCase(
    private val pending: PendingDeepLinkRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<String, DeepLink.NewTask>(dispatcher) {

    override suspend fun execute(params: String): AppResult<DeepLink.NewTask> {
        // Le texte vient d'une autre app (Assistant, raccourci, intent explicite) : borné avant tout traitement.
        val title = params.take(MAX_INPUT_LENGTH)
            .map { if (it.isISOControl() || it.isWhitespace()) ' ' else it }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .take(AddTodoUseCase.MAX_TITLE_LENGTH)
            .trimEnd()
        val draft = DeepLink.NewTask(title)
        pending.set(draft)
        return AppResult.Success(draft)
    }

    private companion object {
        const val MAX_INPUT_LENGTH = 1_000
    }
}
