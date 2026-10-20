package com.mvp.feature.settings.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.settings.domain.model.UserSettings
import com.mvp.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Réglages courants, consentements toujours dans l'ordre de [com.mvp.feature.settings.domain.model.ConsentType]. */
class ObserveSettingsUseCase(
    private val repository: SettingsRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, UserSettings>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<UserSettings>> =
        repository.observeSettings().map { settings ->
            AppResult.Success(settings.copy(consents = settings.consents.sortedBy { it.type.ordinal }))
        }
}
