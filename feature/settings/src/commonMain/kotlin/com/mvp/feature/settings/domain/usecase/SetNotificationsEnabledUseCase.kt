package com.mvp.feature.settings.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Active ou coupe les notifications de l'app. La permission système (iOS / Android 13+)
 * est demandée par l'UI avant d'activer.
 */
class SetNotificationsEnabledUseCase(
    private val repository: SettingsRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<Boolean, Unit>(dispatcher) {

    override suspend fun execute(params: Boolean): AppResult<Unit> = repository.setNotificationsEnabled(params)
}
