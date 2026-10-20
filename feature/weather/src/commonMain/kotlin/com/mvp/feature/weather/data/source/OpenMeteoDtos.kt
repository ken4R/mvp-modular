package com.mvp.feature.weather.data.source

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Réponses de l'API Open-Meteo, limitées aux champs utilisés.

/** `results` est absent quand aucune ville ne correspond. */
@Serializable
internal data class GeocodingResponseDto(
    val results: List<GeocodingResultDto> = emptyList(),
)

@Serializable
internal data class GeocodingResultDto(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
)

@Serializable
internal data class ForecastResponseDto(
    val current: CurrentConditionsDto,
)

@Serializable
internal data class CurrentConditionsDto(
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("apparent_temperature") val apparentTemperature: Double,
    @SerialName("relative_humidity_2m") val relativeHumidity: Int,
    @SerialName("wind_speed_10m") val windSpeed: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("is_day") val isDay: Int,
)
