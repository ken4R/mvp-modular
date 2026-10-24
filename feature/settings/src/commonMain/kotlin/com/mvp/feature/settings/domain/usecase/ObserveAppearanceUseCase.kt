package com.mvp.feature.settings.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.designsystem.Appearance
import com.mvp.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Thème et couleur d'accent choisis dans Réglages, lus par la racine des apps (connecté ou non).
 * Ne réémet que si l'apparence change. Tant que rien n'est arrivé, l'UI garde [Appearance.Default].
 */
class ObserveAppearanceUseCase(
    private val repository: SettingsRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, Appearance>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<Appearance>> =
        repository.observeSettings().map { it.appearance }.distinctUntilChanged().map { AppResult.Success(it) }
}
