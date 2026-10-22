package com.mvp.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.analytics.domain.model.AnalyticsJournal
import com.mvp.core.analytics.domain.usecase.FlushAnalyticsUseCase
import com.mvp.core.analytics.domain.usecase.ObserveAnalyticsJournalUseCase
import com.mvp.core.analytics.domain.usecase.TrackEventUseCase
import com.mvp.core.common.domain.analytics.AnalyticsEvent
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.onFailure
import com.mvp.core.common.domain.result.onSuccess
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.android.ui.applock.DeviceAuthResult
import com.mvp.feature.auth.domain.usecase.LogoutUseCase
import com.mvp.feature.auth.domain.usecase.ObserveSessionUseCase
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.model.ConsentChoice
import com.mvp.feature.settings.domain.model.ConsentType
import com.mvp.feature.settings.domain.model.UserProfile
import com.mvp.feature.settings.domain.usecase.ObserveSettingsUseCase
import com.mvp.feature.settings.domain.usecase.SetAppLockEnabledUseCase
import com.mvp.feature.settings.domain.usecase.SetConsentUseCase
import com.mvp.feature.settings.domain.usecase.SetNotificationsEnabledUseCase
import com.mvp.feature.settings.domain.usecase.UpdateProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val nameInput: String = "",
    val emailInput: String = "",
    val profileMessage: String? = null,
    val notificationsEnabled: Boolean = false,
    val appLockEnabled: Boolean = false,
    val consents: List<Consent> = emptyList(),
    val accountEmail: String? = null,
    val isLoggingOut: Boolean = false,
    /** Journal de mesure, montré dans les builds de debug seulement. */
    val analyticsJournal: AnalyticsJournal = AnalyticsJournal(pending = emptyList(), sent = emptyList()),
    val error: String? = null,
)

class SettingsViewModel(
    observeSettings: ObserveSettingsUseCase,
    observeSession: ObserveSessionUseCase,
    private val updateProfile: UpdateProfileUseCase,
    private val setNotificationsEnabled: SetNotificationsEnabledUseCase,
    private val setConsent: SetConsentUseCase,
    private val setAppLockEnabled: SetAppLockEnabledUseCase,
    private val logout: LogoutUseCase,
    observeAnalyticsJournal: ObserveAnalyticsJournalUseCase,
    private val trackEvent: TrackEventUseCase,
    private val flushAnalytics: FlushAnalyticsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /**
     * Le formulaire de profil suit le profil enregistré tant que l'utilisateur ne le modifie pas,
     * pour ne pas écraser sa saisie en cours.
     */
    private var profileEdited = false

    init {
        observeSettings(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> _state.update { state ->
                        val settings = result.data
                        val withProfile = if (profileEdited) state else state.copy(
                            nameInput = settings.profile.displayName,
                            emailInput = settings.profile.email,
                        )
                        withProfile.copy(
                            notificationsEnabled = settings.notificationsEnabled,
                            appLockEnabled = settings.appLockEnabled,
                            consents = settings.consents,
                        )
                    }
                    is AppResult.Failure -> showError(result.error)
                }
            }
            .launchIn(viewModelScope)
        observeSession(NoParams)
            .onEach { result -> if (result is AppResult.Success) _state.update { it.copy(accountEmail = result.data?.email) } }
            .launchIn(viewModelScope)
        observeAnalyticsJournal(NoParams)
            .onEach { result -> if (result is AppResult.Success) _state.update { it.copy(analyticsJournal = result.data) } }
            .launchIn(viewModelScope)
    }

    fun onNameChange(value: String) {
        profileEdited = true
        _state.update { it.copy(nameInput = value, profileMessage = null, error = null) }
    }

    fun onEmailChange(value: String) {
        profileEdited = true
        _state.update { it.copy(emailInput = value, profileMessage = null, error = null) }
    }

    fun onSaveProfile() = viewModelScope.launch {
        val state = _state.value
        updateProfile(UserProfile(displayName = state.nameInput, email = state.emailInput))
            .onSuccess { profile ->
                profileEdited = false
                _state.update {
                    it.copy(nameInput = profile.displayName, emailInput = profile.email, profileMessage = "Profil enregistré", error = null)
                }
            }
            .onFailure(::showError)
    }

    /** À appeler une fois la permission système accordée (ou pour couper les notifications). */
    fun onNotificationsChange(enabled: Boolean) = viewModelScope.launch {
        setNotificationsEnabled(enabled).onSuccess { clearError() }.onFailure(::showError)
    }

    /** Au retour dans l'app : si les notifications ont été coupées dans les paramètres système, on aligne le réglage. */
    fun onSystemNotificationsChecked(allowed: Boolean) {
        if (!allowed && _state.value.notificationsEnabled) onNotificationsChange(false)
    }

    fun onNotificationPermissionDenied() =
        _state.update { it.copy(error = "Autorisez les notifications dans les paramètres Android") }

    /**
     * Activer comme couper le verrou demande une authentification système réussie : quelqu'un qui
     * trouve l'app ouverte ne peut pas le retirer. Appelé avec le résultat de cette authentification.
     */
    fun onAppLockAuthResult(enabled: Boolean, result: DeviceAuthResult) {
        // Plus aucun code sur l'appareil : le retirer demandait déjà l'ancien code, on laisse couper le verrou.
        val allowed = result == DeviceAuthResult.Success || (!enabled && result == DeviceAuthResult.Unavailable)
        when {
            allowed -> viewModelScope.launch {
                setAppLockEnabled(enabled).onSuccess { clearError() }.onFailure(::showError)
            }
            result == DeviceAuthResult.Failed -> Unit
            else -> _state.update {
                it.copy(error = "Configurez une empreinte, un visage ou un code de verrouillage dans les paramètres Android")
            }
        }
    }

    fun onConsentChange(type: ConsentType, granted: Boolean) = viewModelScope.launch {
        setConsent(ConsentChoice(type, granted)).onSuccess { clearError() }.onFailure(::showError)
    }

    /** Journal de mesure (debug) : un événement de test, ignoré sans consentement comme les autres. */
    fun onSendTestEvent() = viewModelScope.launch { trackEvent(AnalyticsEvent.DebugTest) }

    fun onFlushAnalytics() = viewModelScope.launch { flushAnalytics(NoParams).onFailure(::showError) }

    /** Efface les données locales puis la session ; l'app revient alors sur l'écran de connexion. */
    fun onLogout() {
        if (_state.value.isLoggingOut) return
        _state.update { it.copy(isLoggingOut = true, error = null) }
        viewModelScope.launch {
            logout(NoParams)
                // L'app revient sur la connexion et ce ViewModel est détruit avec la session.
                .onSuccess { _state.update { it.copy(isLoggingOut = false) } }
                .onFailure { error ->
                    _state.update { it.copy(isLoggingOut = false) }
                    showError(error)
                }
        }
    }

    private fun clearError() = _state.update { it.copy(error = null) }

    private fun showError(error: AppError) = _state.update { it.copy(error = error.toMessage(), profileMessage = null) }
}

private fun AppError.toMessage(): String = when (this) {
    is AppError.Validation -> message
    AppError.NotFound -> "Réglage introuvable"
    AppError.Network -> "Problème de connexion"
    AppError.Unauthorized -> "Accès refusé"
    AppError.PermissionDenied -> "Autorisation refusée"
    AppError.Unavailable -> "Service indisponible"
    // Le détail technique d'une exception n'est jamais montré à l'utilisateur.
    is AppError.Unknown -> "Erreur inattendue"
}
