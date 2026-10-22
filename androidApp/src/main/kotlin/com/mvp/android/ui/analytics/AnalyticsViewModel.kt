package com.mvp.android.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.analytics.domain.usecase.FlushAnalyticsUseCase
import com.mvp.core.analytics.domain.usecase.TrackEventUseCase
import com.mvp.core.common.domain.analytics.AnalyticsEvent
import com.mvp.core.common.domain.analytics.AnalyticsScreen
import com.mvp.core.common.domain.usecase.NoParams
import kotlinx.coroutines.launch

/**
 * Mesure d'audience à l'échelle de l'activité : onglets affichés, et envoi de ce qui attend quand l'app
 * passe en arrière-plan. Le consentement est vérifié dans KMP : sans lui, rien n'est gardé.
 */
class AnalyticsViewModel(
    private val trackEvent: TrackEventUseCase,
    private val flushAnalytics: FlushAnalyticsUseCase,
) : ViewModel() {

    fun onScreenShown(screen: AnalyticsScreen) {
        viewModelScope.launch { trackEvent(AnalyticsEvent.ScreenViewed(screen)) }
    }

    fun onBackground() {
        viewModelScope.launch { flushAnalytics(NoParams) }
    }
}
