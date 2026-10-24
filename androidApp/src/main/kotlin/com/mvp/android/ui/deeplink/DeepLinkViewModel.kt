package com.mvp.android.ui.deeplink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.core.deeplink.domain.usecase.ConsumeDeepLinkUseCase
import com.mvp.core.deeplink.domain.usecase.ObservePendingDeepLinkUseCase
import com.mvp.core.deeplink.domain.usecase.OpenDeepLinkUseCase
import com.mvp.feature.todo.domain.usecase.StartTodoDraftUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Liens profonds, à l'échelle de l'activité : l'activité transmet chaque lien reçu, les onglets affichent
 * le lien en attente dès qu'ils sont composés (donc après la connexion et le déverrouillage). Le raccourci vocal
 * « Ajouter une tâche » (ADR 0018) passe par le même chemin : son titre attend comme un lien.
 */
class DeepLinkViewModel(
    private val openDeepLink: OpenDeepLinkUseCase,
    private val consumeDeepLink: ConsumeDeepLinkUseCase,
    observePendingDeepLink: ObservePendingDeepLinkUseCase,
    private val startTodoDraft: StartTodoDraftUseCase,
) : ViewModel() {

    val pending: StateFlow<DeepLink?> = observePendingDeepLink(NoParams)
        .map { (it as? AppResult.Success)?.data }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun open(url: String) {
        viewModelScope.launch { openDeepLink(url) }
    }

    /** Titre dicté : prérempli dans « Nouvelle tâche », rien n'est ajouté sans le bouton « Ajouter ». */
    fun startDraft(title: String) {
        viewModelScope.launch { startTodoDraft(title) }
    }

    fun consume(link: DeepLink) {
        viewModelScope.launch { consumeDeepLink(link) }
    }
}
