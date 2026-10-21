package com.mvp.feature.settings.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.settings.domain.repository.AppLockRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * `true` si l'app doit afficher l'écran de déverrouillage au lieu de son contenu.
 * Tant qu'aucune valeur n'est arrivée, ou en cas d'erreur, l'UI doit rester masquée.
 */
class ObserveAppLockUseCase(
    private val repository: AppLockRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, Boolean>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<Boolean>> =
        repository.observeLocked().map { AppResult.Success(it) }
}
