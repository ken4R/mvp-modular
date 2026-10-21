package com.mvp.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.onFailure
import com.mvp.core.common.domain.result.onSuccess
import com.mvp.feature.auth.domain.model.Credentials
import com.mvp.feature.auth.domain.usecase.LoginUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    // Le mot de passe ne doit jamais apparaître dans un log ou un rapport de crash.
    override fun toString(): String = "LoginUiState(email=$email, password=***, isLoading=$isLoading, error=$error)"
}

class LoginViewModel(private val login: LoginUseCase) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onLogin() {
        val state = _state.value
        if (state.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            login(Credentials(email = state.email, password = state.password))
                // Le formulaire est vidé : il ne doit rien rester à l'écran après une déconnexion.
                .onSuccess { _state.value = LoginUiState() }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.toMessage(),
                            password = if (error == AppError.Unauthorized) "" else it.password,
                        )
                    }
                }
        }
    }
}

private fun AppError.toMessage(): String = when (this) {
    is AppError.Validation -> message
    AppError.Unauthorized -> "E-mail ou mot de passe incorrect"
    AppError.PermissionDenied -> "Autorisation refusée"
    AppError.Unavailable -> "Service indisponible"
    AppError.Network -> "Problème de connexion"
    AppError.NotFound -> "Compte introuvable"
    // Le détail technique d'une exception n'est jamais montré à l'utilisateur.
    is AppError.Unknown -> "Erreur inattendue"
}
