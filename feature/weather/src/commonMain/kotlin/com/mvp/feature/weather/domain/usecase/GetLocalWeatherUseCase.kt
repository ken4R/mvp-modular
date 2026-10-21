package com.mvp.feature.weather.domain.usecase

import com.mvp.core.common.domain.location.GeoPosition
import com.mvp.core.common.domain.location.LocationProvider
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.weather.domain.model.LocalWeather
import com.mvp.feature.weather.domain.repository.WeatherRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.math.round

/**
 * Météo actuelle à la position de l'appareil.
 * Règle métier : la position est arrondie à 2 décimales (environ 1 km) avant de quitter l'appareil,
 * précision suffisante pour la météo. Rien n'est enregistré.
 */
class GetLocalWeatherUseCase(
    private val location: LocationProvider,
    private val repository: WeatherRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, LocalWeather>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<LocalWeather> =
        when (val position = location.currentPosition()) {
            is AppResult.Failure -> position
            is AppResult.Success -> {
                val approximate = position.data.approximate()
                when (val weather = repository.getCurrentWeather(approximate.latitude, approximate.longitude)) {
                    is AppResult.Failure -> weather
                    is AppResult.Success -> AppResult.Success(LocalWeather(approximate, weather.data))
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
