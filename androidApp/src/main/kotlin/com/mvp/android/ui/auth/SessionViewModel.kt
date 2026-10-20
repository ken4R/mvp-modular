package com.mvp.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.UserAccount
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.auth.domain.usecase.ObserveSessionUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SessionUiState {
    /** Session pas encore lue : rien à afficher, pour ne pas faire clignoter l'écran de connexion. */
    data object Loading : SessionUiState
    data object LoggedOut : SessionUiState
    data class LoggedIn(val account: UserAccount) : SessionUiState
}

/** Décide de l'écran racine : connexion ou onglets de l'app. */
class SessionViewModel(observeSession: ObserveSessionUseCase) : ViewModel() {

    val state: StateFlow<SessionUiState> = observeSession(NoParams)
        .map { result ->
            when (result) {
                is AppResult.Success -> result.data?.let { SessionUiState.LoggedIn(it) } ?: SessionUiState.LoggedOut
                // Session illisible : on redemande la connexion plutôt que d'ouvrir l'app.
                is AppResult.Failure -> SessionUiState.LoggedOut
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionUiState.Loading)
}
