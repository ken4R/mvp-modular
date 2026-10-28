package com.mvp.feature.weather.domain.usecase

import com.mvp.core.common.domain.location.GeoPosition
import com.mvp.core.common.domain.location.LocationProvider
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.weather.domain.model.CachedLocalWeather
import com.mvp.feature.weather.domain.model.LocalWeather
import com.mvp.feature.weather.domain.repository.LocalWeatherCache
import com.mvp.feature.weather.domain.repository.WeatherRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.math.round

/**
 * Météo actuelle à la position de l'appareil.
 * Règle métier : la position est arrondie à 2 décimales (environ 1 km) avant de quitter l'appareil,
 * précision suffisante pour la météo. Seule la dernière météo obtenue est gardée sur l'appareil, avec cette
 * position arrondie, pour le widget Météo (ADR 0026) ; elle est effacée à la déconnexion.
 */
class GetLocalWeatherUseCase(
    private val location: LocationProvider,
    private val repository: WeatherRepository,
    private val cache: LocalWeatherCache,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, LocalWeather>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<LocalWeather> =
        when (val position = location.currentPosition()) {
            is AppResult.Failure -> position
            is AppResult.Success -> {
                val approximate = position.data.approximate()
                when (val weather = repository.getCurrentWeather(approximate.latitude, approximate.longitude)) {
                    is AppResult.Failure -> weather
                    is AppResult.Success -> {
                        val local = LocalWeather(approximate, weather.data)
                        // Le widget n'est qu'un plus : une écriture impossible ne prive pas l'écran de sa météo.
                        runCatching { cache.save(CachedLocalWeather(local, now())) }
                        AppResult.Success(local)
                    }
                }
            }
        }

    private fun GeoPosition.approximate() = GeoPosition(latitude.approximate(), longitude.approximate())

    private fun Double.approximate() = round(this * COORDINATE_SCALE) / COORDINATE_SCALE

    private companion object {
        /** 2 décimales. */
        const val COORDINATE_SCALE = 100.0
    }
}
