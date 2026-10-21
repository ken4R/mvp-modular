package com.mvp.feature.settings.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.settings.domain.repository.AppLockRepository
import com.mvp.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Active ou coupe le verrou Face ID / Touch ID / empreinte. L'UI fait réussir une authentification
 * système avant d'activer : l'app est donc déverrouillée, l'écran ne se referme pas aussitôt.
 */
class SetAppLockEnabledUseCase(
    private val settings: SettingsRepository,
    private val appLock: AppLockRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<Boolean, Unit>(dispatcher) {

    override suspend fun execute(params: Boolean): AppResult<Unit> {
        if (params) appLock.unlock()
        return settings.setAppLockEnabled(params)
    }
}
