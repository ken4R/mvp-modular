package com.mvp.feature.weather.domain.repository

import com.mvp.feature.weather.domain.model.CachedLocalWeather
import kotlinx.coroutines.flow.Flow

/** Dernière météo « Ma position », pour le widget (ADR 0026). Effacée à la déconnexion. */
interface LocalWeatherCache {
    /** `null` sans météo gardée. */
    fun observe(): Flow<CachedLocalWeather?>
    fun save(weather: CachedLocalWeather)
    fun clear()
}
