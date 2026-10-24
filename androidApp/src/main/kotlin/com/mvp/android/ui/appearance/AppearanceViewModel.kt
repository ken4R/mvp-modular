package com.mvp.android.ui.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.designsystem.Appearance
import com.mvp.feature.settings.domain.usecase.ObserveAppearanceUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn

/**
 * Thème et accent choisis dans Réglages, pour toute l'app (connecté ou non) : lié à l'activité, pas à la session.
 * Une erreur de lecture garde l'apparence précédente (par défaut : thème de l'appareil, accent bleu).
 */
class AppearanceViewModel(observeAppearance: ObserveAppearanceUseCase) : ViewModel() {

    val appearance: StateFlow<Appearance> = observeAppearance(NoParams)
        .mapNotNull { it.getOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Appearance.Default)
}
