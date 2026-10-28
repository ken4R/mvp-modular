package com.mvp.feature.weather.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.SessionExpiry
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.deeplink.domain.DeepLinks
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.feature.weather.domain.model.CachedLocalWeather
import com.mvp.feature.weather.domain.model.WeatherWidgetState
import com.mvp.feature.weather.domain.repository.LocalWeatherCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlin.math.min

/**
 * Contenu du widget Météo (ADR 0026), recalculé à chaque nouvelle météo et à chaque changement de session.
 * Session absente ou expirée : [WeatherWidgetState.SignedOut]. Pas de météo gardée, ou plus vieille que
 * [MAX_AGE_MS] : [WeatherWidgetState.NoData]. Le verrou Face ID / empreinte ne masque pas la météo : elle ne dit rien
 * du compte, et la position n'est pas affichée.
 */
class ObserveWeatherWidgetUseCase(
    private val cache: LocalWeatherCache,
    private val session: SessionExpiry,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, WeatherWidgetState>(dispatcher) {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun execute(params: NoParams): Flow<AppResult<WeatherWidgetState>> =
        combine(session.observeExpiresAt(), cache.observe()) { expiresAt, cached -> expiresAt to cached }
            // Rien ne réémet quand la session expire ou que la météo vieillit : on recalcule nous-mêmes à l'échéance.
            .transformLatest { (expiresAt, cached) ->
                while (true) {
                    val state = state(expiresAt, cached)
                    emit(state)
                    if (state !is WeatherWidgetState.Weather) break
                    delay(state.visibleUntil - now())
                }
            }
            .distinctUntilChanged()
            .map { AppResult.Success(it) }

    private fun state(expiresAt: Long?, cached: CachedLocalWeather?): WeatherWidgetState {
        val current = now()
        if (expiresAt == null || expiresAt <= current) return WeatherWidgetState.SignedOut
        if (cached == null) return WeatherWidgetState.NoData
        val visibleUntil = min(cached.fetchedAt + MAX_AGE_MS, expiresAt)
        if (visibleUntil <= current) return WeatherWidgetState.NoData
        val weather = cached.local.weather
        return WeatherWidgetState.Weather(
            temperatureCelsius = weather.temperatureCelsius,
            condition = weather.condition,
            isDay = weather.isDay,
            updatedAt = cached.fetchedAt,
            visibleUntil = visibleUntil,
            link = DeepLinks.url(DeepLink.Weather),
        )
    }

    companion object {
        /** Au-delà, une météo « actuelle » induirait en erreur : le widget invite à ouvrir l'app. */
        const val MAX_AGE_MS = 3 * 60 * 60 * 1000L
    }
}
