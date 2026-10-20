package com.mvp.feature.weather.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.weather.domain.model.City
import com.mvp.feature.weather.domain.model.CurrentWeather
import com.mvp.feature.weather.domain.repository.WeatherRepository
import kotlinx.coroutines.CoroutineDispatcher

/** Météo actuelle d'une ville. */
class GetCurrentWeatherUseCase(
    private val repository: WeatherRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<City, CurrentWeather>(dispatcher) {

    override suspend fun execute(params: City): AppResult<CurrentWeather> =
        repository.getCurrentWeather(params.latitude, params.longitude)
}
