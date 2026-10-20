package com.mvp.feature.settings.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher

/** Accorde ou retire un consentement. */
class SetConsentUseCase(
    private val repository: SettingsRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<Consent, Unit>(dispatcher) {

    override suspend fun execute(params: Consent): AppResult<Unit> = repository.setConsent(params)
}
