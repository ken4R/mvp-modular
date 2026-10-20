package com.mvp.feature.weather.data.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** Accès distant à la météo. Les exceptions Ktor remontent : le repository les convertit. */
internal interface WeatherRemoteDataSource {
    suspend fun searchCities(query: String): List<GeocodingResultDto>
    suspend fun getCurrentConditions(latitude: Double, longitude: Double): CurrentConditionsDto
}

/** Open-Meteo : gratuit, sans clé d'API, en HTTPS. */
internal class OpenMeteoRemoteDataSource(private val client: HttpClient) : WeatherRemoteDataSource {

    override suspend fun searchCities(query: String): List<GeocodingResultDto> =
        client.get(GEOCODING_URL) {
            parameter("name", query)
            parameter("count", MAX_RESULTS)
            parameter("language", "fr")
            parameter("format", "json")
        }.body<GeocodingResponseDto>().results

    override suspend fun getCurrentConditions(latitude: Double, longitude: Double): CurrentConditionsDto =
        client.get(FORECAST_URL) {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("current", CURRENT_FIELDS)
            parameter("timezone", "auto")
        }.body<ForecastResponseDto>().current

    companion object {
        const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        private const val MAX_RESULTS = 10
        private const val CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day"
    }
}
