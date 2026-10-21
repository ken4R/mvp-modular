package com.mvp.android.ui.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.onFailure
import com.mvp.core.common.domain.result.onSuccess
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.auth.domain.usecase.LogoutUseCase
import com.mvp.feature.settings.domain.usecase.LockAppUseCase
import com.mvp.feature.settings.domain.usecase.ObserveAppLockUseCase
import com.mvp.feature.settings.domain.usecase.ObserveSettingsUseCase
import com.mvp.feature.settings.domain.usecase.UnlockAppUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppLockUiState(
    /** Rien ne s'affiche (ni contenu ni écran verrouillé) avant la première lecture de l'état. */
    val loaded: Boolean = false,
    val locked: Boolean = true,
    /** Réglage activé : l'app est aussi masquée dans l'écran des apps récentes. */
    val enabled: Boolean = false,
    val message: String? = null,
    val isLoggingOut: Boolean = false,
)

/** Verrou Face ID / empreinte : lié à l'activité, il survit aux rotations mais pas à la mort du process. */
class AppLockViewModel(
    observeAppLock: ObserveAppLockUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val lockApp: LockAppUseCase,
    private val unlockApp: UnlockAppUseCase,
    private val logout: LogoutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AppLockUiState())
    val state: StateFlow<AppLockUiState> = _state.asStateFlow()

    /** Une seule ouverture automatique du prompt par verrouillage : après une annulation, c'est le bouton. */
    private var autoPromptPending = true

    init {
        observeAppLock(NoParams)
            .onEach { result ->
                // Erreur de lecture : on reste verrouillé, la déconnexion reste possible.
                val locked = (result as? AppResult.Success)?.data ?: true
                _state.update { it.copy(loaded = true, locked = locked, message = if (locked) it.message else null) }
            }
            .launchIn(viewModelScope)
        observeSettings(NoParams)
            .onEach { result -> if (result is AppResult.Success) _state.update { it.copy(enabled = result.data.appLockEnabled) } }
            .launchIn(viewModelScope)
    }

    /** L'app passe en arrière-plan (hors rotation) : il faudra se ré-authentifier. */
    fun onBackground() {
        autoPromptPending = true
        // Masqué tout de suite, sans attendre le use case : rien ne réapparaît si le process est gelé entre-temps.
        _state.update { if (it.enabled) it.copy(locked = true) else it }
        viewModelScope.launch { lockApp(NoParams) }
    }

    /** `true` une seule fois par verrouillage : l'écran verrouillé ouvre alors le prompt tout seul. */
    fun consumeAutoPrompt(): Boolean = autoPromptPending.also { autoPromptPending = false }

    fun onAuthResult(result: DeviceAuthResult) {
        when (result) {
            DeviceAuthResult.Success -> viewModelScope.launch {
                unlockApp(NoParams)
                    .onSuccess { _state.update { it.copy(locked = false, message = null) } }
                    .onFailure { _state.update { it.copy(message = "Déverrouillage impossible, réessayez") } }
            }
            DeviceAuthResult.Failed -> _state.update { it.copy(message = null) }
            DeviceAuthResult.Unavailable -> _state.update {
                it.copy(message = "Aucune empreinte, aucun visage ni code n'est configuré sur cet appareil. Déconnectez-vous puis reconnectez-vous.")
            }
        }
    }

    /** Repli si l'authentification est impossible : efface les données de l'appareil puis revient à la connexion. */
    fun onLogout() {
        if (_state.value.isLoggingOut) return
        _state.update { it.copy(isLoggingOut = true) }
        viewModelScope.launch {
            logout(NoParams).onFailure { _state.update { it.copy(message = "Déconnexion impossible, réessayez") } }
            _state.update { it.copy(isLoggingOut = false) }
        }
    }
}
