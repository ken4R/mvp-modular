package com.mvp.feature.settings.domain.usecase

import com.mvp.core.common.domain.analytics.AnalyticsConsentObserver
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.model.ConsentChoice
import com.mvp.feature.settings.domain.model.ConsentTexts
import com.mvp.feature.settings.domain.model.ConsentType
import com.mvp.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Accorde ou retire un consentement. Règles métier : chaque choix est daté et lié à la version du
 * texte affiché ; retirer la mesure d'audience l'arrête tout de suite (les [analyticsObservers]
 * abandonnent ce qui attendait d'être envoyé) avant de confirmer le choix à l'utilisateur.
 */
class SetConsentUseCase(
    private val repository: SettingsRepository,
    private val analyticsObservers: List<AnalyticsConsentObserver>,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : UseCase<ConsentChoice, Unit>(dispatcher) {

    override suspend fun execute(params: ConsentChoice): AppResult<Unit> {
        val consent = Consent(
            type = params.type,
            granted = params.granted,
            decidedAt = now(),
            textVersion = ConsentTexts.of(params.type).version,
        )
        val result = repository.setConsent(consent)
        // Même si l'enregistrement échoue : l'utilisateur a demandé l'arrêt, la file est vidée.
        if (params.type == ConsentType.Analytics && !params.granted) {
            analyticsObservers.forEach { it.onAnalyticsConsentRevoked() }
        }
        return result
    }
}
