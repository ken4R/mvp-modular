package com.mvp.feature.weather.domain.repository

import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.weather.domain.model.City
import com.mvp.feature.weather.domain.model.CurrentWeather

interface WeatherRepository {
    suspend fun searchCities(query: String): AppResult<List<City>>
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): AppResult<CurrentWeather>
}
