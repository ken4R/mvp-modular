package com.mvp.feature.weather.domain.model

data class CurrentWeather(
    val temperatureCelsius: Double,
    val feelsLikeCelsius: Double,
    val humidityPercent: Int,
    val windSpeedKmh: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
)

/** Conditions simplifiées, que chaque plateforme traduit en icône et en libellé. */
enum class WeatherCondition {
    Clear,
    PartlyCloudy,
    Cloudy,
    Fog,
    Drizzle,
    Rain,
    Snow,
    Thunderstorm,
    Unknown,
}
