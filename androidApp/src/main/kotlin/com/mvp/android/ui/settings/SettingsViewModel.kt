package com.mvp.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.onFailure
import com.mvp.core.common.domain.result.onSuccess
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.model.ConsentType
import com.mvp.feature.settings.domain.model.UserProfile
import com.mvp.feature.settings.domain.usecase.ObserveSettingsUseCase
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
    val consents: List<Consent> = emptyList(),
    val error: String? = null,
)

class SettingsViewModel(
    observeSettings: ObserveSettingsUseCase,
    private val updateProfile: UpdateProfileUseCase,
    private val setNotificationsEnabled: SetNotificationsEnabledUseCase,
    private val setConsent: SetConsentUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** Le formulaire de profil n'est pré-rempli qu'une fois, pour ne pas écraser la saisie en cours. */
    private var profileLoaded = false

    init {
        observeSettings(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> _state.update { state ->
                        val settings = result.data
                        val withProfile = if (profileLoaded) state else state.copy(
                            nameInput = settings.profile.displayName,
                            emailInput = settings.profile.email,
                        )
                        profileLoaded = true
                        withProfile.copy(notificationsEnabled = settings.notificationsEnabled, consents = settings.consents)
                    }
                    is AppResult.Failure -> showError(result.error)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onNameChange(value: String) = _state.update { it.copy(nameInput = value, profileMessage = null, error = null) }

    fun onEmailChange(value: String) = _state.update { it.copy(emailInput = value, profileMessage = null, error = null) }

    fun onSaveProfile() = viewModelScope.launch {
        val state = _state.value
        updateProfile(UserProfile(displayName = state.nameInput, email = state.emailInput))
            .onSuccess { profile ->
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

    fun onConsentChange(type: ConsentType, granted: Boolean) = viewModelScope.launch {
        setConsent(Consent(type, granted)).onSuccess { clearError() }.onFailure(::showError)
    }

    private fun clearError() = _state.update { it.copy(error = null) }

    private fun showError(error: AppError) = _state.update { it.copy(error = error.toMessage(), profileMessage = null) }
}

private fun AppError.toMessage(): String = when (this) {
    is AppError.Validation -> message
    AppError.NotFound -> "Réglage introuvable"
    AppError.Network -> "Problème de connexion"
    AppError.Unauthorized -> "Accès refusé"
    // Le détail technique d'une exception n'est jamais montré à l'utilisateur.
    is AppError.Unknown -> "Erreur inattendue"
}
