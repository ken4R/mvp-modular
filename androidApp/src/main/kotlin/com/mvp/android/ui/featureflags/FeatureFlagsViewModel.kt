package com.mvp.android.ui.featureflags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.featureflags.domain.model.FeatureFlag
import com.mvp.core.featureflags.domain.model.FeatureFlagOverride
import com.mvp.core.featureflags.domain.model.FeatureFlagState
import com.mvp.core.featureflags.domain.model.FeatureStage
import com.mvp.core.featureflags.domain.model.RemoteFlagsRefresh
import com.mvp.core.featureflags.domain.usecase.ClearFeatureFlagOverridesUseCase
import com.mvp.core.featureflags.domain.usecase.ObserveFeatureFlagsUseCase
import com.mvp.core.featureflags.domain.usecase.RefreshRemoteFlagsUseCase
import com.mvp.core.featureflags.domain.usecase.SetFeatureFlagOverrideUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeatureFlagsUiState(
    /** Vide tant que le cache n'est pas lu : [isEnabled] donne alors la valeur par défaut. */
    val flags: List<FeatureFlagState> = emptyList(),
    val refreshing: Boolean = false,
    val message: String? = null,
) {
    fun isEnabled(flag: FeatureFlag): Boolean =
        flags.firstOrNull { it.flag == flag }?.enabled ?: (flag.stage == FeatureStage.Released && flag.defaultEnabled)
}

/** Feature flags (ADR 0021) : valeurs pour masquer les features, et actions du menu « Feature flags (debug) ». */
class FeatureFlagsViewModel(
    observeFlags: ObserveFeatureFlagsUseCase,
    private val refreshRemote: RefreshRemoteFlagsUseCase,
    private val setOverride: SetFeatureFlagOverrideUseCase,
    private val clearOverrides: ClearFeatureFlagOverridesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(FeatureFlagsUiState())
    val state: StateFlow<FeatureFlagsUiState> = _state.asStateFlow()

    init {
        observeFlags(NoParams)
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> _state.update { it.copy(flags = result.data) }
                    is AppResult.Failure -> showError(result.error)
                }
            }
            .launchIn(viewModelScope)
    }

    /** Au lancement et au retour au premier plan ; ne lit le distant qu'une fois toutes les 15 minutes. */
    fun refresh() = viewModelScope.launch { refreshRemote(RemoteFlagsRefresh()) }

    /** « Rafraîchir le distant » du menu debug : passe outre le délai et dit si la lecture a marché. */
    fun forceRefresh() = viewModelScope.launch {
        _state.update { it.copy(refreshing = true, message = null) }
        val message = when (val result = refreshRemote(RemoteFlagsRefresh(force = true))) {
            is AppResult.Success -> "Flags distants à jour"
            is AppResult.Failure -> result.error.toMessage()
        }
        _state.update { it.copy(refreshing = false, message = message) }
    }

    fun onOverride(flag: FeatureFlag, enabled: Boolean) = viewModelScope.launch {
        (setOverride(FeatureFlagOverride(flag, enabled)) as? AppResult.Failure)?.let { showError(it.error) }
    }

    fun onReset() = viewModelScope.launch {
        (clearOverrides(NoParams) as? AppResult.Failure)?.let { showError(it.error) }
    }

    private fun showError(error: AppError) = _state.update { it.copy(message = error.toMessage()) }

    private fun AppError.toMessage(): String = when (this) {
        AppError.Network -> "Flags distants injoignables : dernières valeurs connues gardées"
        is AppError.Validation -> message
        else -> "Flags indisponibles pour le moment : dernières valeurs connues gardées"
    }
}
