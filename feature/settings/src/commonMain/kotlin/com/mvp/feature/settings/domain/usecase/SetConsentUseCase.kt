package com.mvp.feature.settings.domain.usecase

import com.mvp.core.common.domain.analytics.AnalyticsConsentObserver
import com.mvp.core.common.domain.crash.CrashReportingConsentObserver
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.model.ConsentChoice
import com.mvp.feature.settings.domain.model.ConsentTexts
import com.mvp.feature.settings.domain.model.ConsentType
import com.mvp.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.cancellation.CancellationException

/**
 * Accorde ou retire un consentement. Règles métier : chaque choix est daté et lié à la version du
 * texte affiché ; retirer la mesure d'audience l'arrête tout de suite (les [analyticsObservers]
 * abandonnent ce qui attendait d'être envoyé) avant de confirmer le choix à l'utilisateur. De même pour les
 * rapports de plantage ([crashObservers] : plus rien n'est enregistré, les rapports en attente sont effacés).
 */
class SetConsentUseCase(
    private val repository: SettingsRepository,
    private val analyticsObservers: List<AnalyticsConsentObserver>,
    private val crashObservers: List<CrashReportingConsentObserver>,
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
        // Même si l'enregistrement échoue : l'utilisateur a demandé l'arrêt, la file est vidée. Le consentement
        // reste alors « accordé » en base (l'écran affiche l'erreur et l'interrupteur reste activé) : à refaire.
        if (!params.granted) {
            when (params.type) {
                ConsentType.Analytics -> analyticsObservers.forEach { notify { it.onAnalyticsConsentRevoked() } }
                ConsentType.CrashReports -> crashObservers.forEach { notify { it.onCrashReportingConsentRevoked() } }
                ConsentType.Marketing, ConsentType.Personalization -> Unit
            }
        }
        return result
    }

    // Un observateur en échec n'empêche pas de prévenir les autres, ni ne change le résultat.
    private suspend fun notify(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
    }
}
