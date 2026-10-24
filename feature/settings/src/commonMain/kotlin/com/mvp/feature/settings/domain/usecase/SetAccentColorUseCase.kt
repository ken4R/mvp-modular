package com.mvp.feature.settings.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.core.designsystem.AccentColor
import com.mvp.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher

/** Couleur d'accent de l'app, en clair comme en sombre. Le thème ne change pas. */
class SetAccentColorUseCase(
    private val repository: SettingsRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<AccentColor, Unit>(dispatcher) {

    override suspend fun execute(params: AccentColor): AppResult<Unit> = repository.setAccentColor(params)
}
