package com.mvp.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.android.ui.applock.DeviceAuthResult
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.result.onFailure
import com.mvp.core.common.domain.result.onSuccess
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.auth.domain.usecase.ObserveSessionUseCase
import com.mvp.feature.onboarding.domain.model.OnboardingDevice
import com.mvp.feature.onboarding.domain.model.OnboardingStep
import com.mvp.feature.onboarding.domain.usecase.CompleteOnboardingUseCase
import com.mvp.feature.onboarding.domain.usecase.GetOnboardingStepsUseCase
import com.mvp.feature.onboarding.domain.usecase.ObserveOnboardingRequiredUseCase
import com.mvp.feature.settings.domain.model.ConsentChoice
import com.mvp.feature.settings.domain.model.ConsentType
import com.mvp.feature.settings.domain.usecase.SetAppLockEnabledUseCase
import com.mvp.feature.settings.domain.usecase.SetConsentUseCase
import com.mvp.feature.settings.domain.usecase.SetNotificationsEnabledUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    /** Étapes de cet appareil (vide tant qu'elles ne sont pas lues). */
    val steps: List<OnboardingStep> = emptyList(),
    val index: Int = 0,
    /** Une action est en cours (enregistrement), ou la présentation est terminée : les boutons sont désactivés. */
    val busy: Boolean = false,
    /** Terminée et enregistrée : les onglets vont remplacer l'écran, plus rien ne doit être enregistré d'ici là. */
    val finished: Boolean = false,
    val error: String? = null,
) {
    val step: OnboardingStep? get() = steps.getOrNull(index)
    val enabled: Boolean get() = !busy && !finished
}

/**
 * Présentation du premier lancement (ADR 0016), affichée après la connexion et avant les onglets. Liée à l'activité :
 * son état « vue » est celui de l'appareil, pas de la session. Les choix (notifications, verrou, consentement)
 * sont enregistrés pour le compte connecté par les use cases de feature:settings, comme depuis Réglages.
 */
class OnboardingViewModel(
    observeRequired: ObserveOnboardingRequiredUseCase,
    observeSession: ObserveSessionUseCase,
    private val getSteps: GetOnboardingStepsUseCase,
    private val complete: CompleteOnboardingUseCase,
    private val setNotificationsEnabled: SetNotificationsEnabledUseCase,
    private val setAppLockEnabled: SetAppLockEnabledUseCase,
    private val setConsent: SetConsentUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /**
     * `null` tant que l'état n'est pas lu. Illisible : la présentation n'est pas imposée (l'app reste utilisable),
     * elle reste accessible depuis Réglages. Remontrée (« Revoir la présentation ») : elle repart de la première étape.
     */
    val required: StateFlow<Boolean?> = observeRequired(NoParams)
        .map { it.getOrNull() ?: false }
        .onEach { required -> if (required) restart() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Lié à l'activité : un autre compte (déconnexion depuis l'écran verrouillé) repart de la première étape.
        observeSession(NoParams)
            .map { it.getOrNull() }
            .distinctUntilChanged()
            .drop(1)
            .onEach { restart() }
            .launchIn(viewModelScope)
    }

    /** À appeler une fois à l'affichage : le verrou n'est proposé que si l'appareil a une biométrie ou un code. */
    fun loadSteps(appLockAvailable: Boolean) = viewModelScope.launch {
        val steps = getSteps(OnboardingDevice(appLockAvailable)).getOrNull()
            ?: OnboardingStep.entries.filter { it != OnboardingStep.AppLock }
        _state.update { it.copy(steps = steps, index = it.index.coerceAtMost(steps.lastIndex)) }
    }

    /** Étape suivante, ou fin de la présentation après la dernière. */
    fun onNext() = runBusy { advance() }

    /** « Passer » : la présentation n'est plus montrée sur cet appareil. */
    fun onSkip() = runBusy { finish() }

    /** Résultat de la demande système. Accordée : les rappels sont activés comme depuis Réglages. */
    fun onNotificationsResult(granted: Boolean) {
        if (!granted) return onNext()
        runBusy { save(setNotificationsEnabled(true)) }
    }

    /** Le verrou n'est activé qu'après une authentification réussie (on vérifie qu'elle marche sur l'appareil). */
    fun onAppLockAuthResult(result: DeviceAuthResult) {
        when (result) {
            DeviceAuthResult.Success -> runBusy { save(setAppLockEnabled(true)) }
            // Annulé : l'utilisateur reste sur l'étape, il peut réessayer ou choisir « Plus tard ».
            DeviceAuthResult.Failed -> Unit
            DeviceAuthResult.Unavailable -> onNext()
        }
    }

    /** Accepté ou refusé explicitement : le choix est daté avec la version du texte affiché (ConsentTexts). */
    fun onConsentChoice(step: OnboardingStep, granted: Boolean) {
        val type = step.consentType ?: return onNext()
        runBusy { save(setConsent(ConsentChoice(type, granted))) }
    }

    private suspend fun save(result: AppResult<Unit>) {
        if (result is AppResult.Success) advance() else showSaveError()
    }

    private suspend fun advance() {
        val state = _state.value
        if (state.index >= state.steps.lastIndex) finish() else _state.update { it.copy(index = it.index + 1) }
    }

    // Une fois enregistrée, `required` passe à false et l'app affiche les onglets.
    private suspend fun finish() {
        complete(NoParams)
            .onSuccess { _state.update { it.copy(finished = true) } }
            .onFailure { showSaveError() }
    }

    private fun restart() = _state.update { it.copy(index = 0, finished = false, error = null) }

    private fun showSaveError() = _state.update { it.copy(error = "Impossible d'enregistrer, réessayez") }

    private fun runBusy(block: suspend () -> Unit) {
        if (!_state.value.enabled) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}

/** Consentement demandé par cette étape (Mesure d'audience, Rapports de plantage), `null` pour les autres. */
val OnboardingStep.consentType: ConsentType?
    get() = when (this) {
        OnboardingStep.Analytics -> ConsentType.Analytics
        OnboardingStep.CrashReports -> ConsentType.CrashReports
        OnboardingStep.Welcome, OnboardingStep.Notifications, OnboardingStep.Location, OnboardingStep.AppLock -> null
    }
