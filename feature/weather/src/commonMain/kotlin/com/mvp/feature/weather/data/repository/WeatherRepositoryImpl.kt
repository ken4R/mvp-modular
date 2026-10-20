package com.mvp.feature.weather.data.repository

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.network.safeApiCall
import com.mvp.feature.weather.data.source.CurrentConditionsDto
import com.mvp.feature.weather.data.source.GeocodingResultDto
import com.mvp.feature.weather.data.source.WeatherRemoteDataSource
import com.mvp.feature.weather.domain.model.City
import com.mvp.feature.weather.domain.model.CurrentWeather
import com.mvp.feature.weather.domain.model.WeatherCondition
import com.mvp.feature.weather.domain.repository.WeatherRepository

internal class WeatherRepositoryImpl(
    private val remote: WeatherRemoteDataSource,
) : WeatherRepository {

    override suspend fun searchCities(query: String): AppResult<List<City>> = safeApiCall {
        remote.searchCities(query).map { it.toDomain() }
    }

    override suspend fun getCurrentWeather(latitude: Double, longitude: Double): AppResult<CurrentWeather> =
        safeApiCall { remote.getCurrentConditions(latitude, longitude).toDomain() }
}

private fun GeocodingResultDto.toDomain() = City(
    id = id,
    name = name,
    region = admin1,
    country = country,
    latitude = latitude,
    longitude = longitude,
)

private fun CurrentConditionsDto.toDomain() = CurrentWeather(
    temperatureCelsius = temperature,
    feelsLikeCelsius = apparentTemperature,
    humidityPercent = relativeHumidity,
    windSpeedKmh = windSpeed,
    condition = weatherConditionOf(weatherCode),
    isDay = isDay == 1,
)

/** Codes météo WMO renvoyés par Open-Meteo (https://open-meteo.com/en/docs, « WMO Weather interpretation codes »). */
internal fun weatherConditionOf(wmoCode: Int): WeatherCondition = when (wmoCode) {
    0 -> WeatherCondition.Clear
    1, 2 -> WeatherCondition.PartlyCloudy
    3 -> WeatherCondition.Cloudy
    45, 48 -> WeatherCondition.Fog
    51, 53, 55, 56, 57 -> WeatherCondition.Drizzle
    61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.Rain
    71, 73, 75, 77, 85, 86 -> WeatherCondition.Snow
    95, 96, 99 -> WeatherCondition.Thunderstorm
    else -> WeatherCondition.Unknown
}
