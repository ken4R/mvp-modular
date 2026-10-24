package com.mvp.feature.settings.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.core.designsystem.ThemeMode
import com.mvp.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher

/** Thème de l'app : celui de l'appareil, clair ou sombre. La couleur d'accent ne change pas. */
class SetThemeModeUseCase(
    private val repository: SettingsRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<ThemeMode, Unit>(dispatcher) {

    override suspend fun execute(params: ThemeMode): AppResult<Unit> = repository.setThemeMode(params)
}
