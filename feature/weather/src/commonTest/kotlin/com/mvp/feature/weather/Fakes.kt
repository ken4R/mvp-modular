package com.mvp.feature.weather

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.location.GeoPosition
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.session.SessionExpiry
import com.mvp.core.securestorage.SecureStorage
import com.mvp.feature.weather.domain.model.CachedLocalWeather
import com.mvp.feature.weather.domain.model.City
import com.mvp.feature.weather.domain.model.CurrentWeather
import com.mvp.feature.weather.domain.repository.LocalWeatherCache
import com.mvp.feature.weather.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Réponse météo pilotée par le test ; garde les positions demandées. */
class FakeWeatherRepository : WeatherRepository {
    var weather: AppResult<CurrentWeather> = AppResult.Failure(AppError.Unknown())
    val requests = mutableListOf<GeoPosition>()

    override suspend fun searchCities(query: String): AppResult<List<City>> = AppResult.Success(emptyList())

    override suspend fun getCurrentWeather(latitude: Double, longitude: Double): AppResult<CurrentWeather> {
        requests += GeoPosition(latitude, longitude)
        return weather
    }
}

class InMemoryLocalWeatherCache : LocalWeatherCache {
    val value = MutableStateFlow<CachedLocalWeather?>(null)
    var failOnSave = false

    override fun observe(): Flow<CachedLocalWeather?> = value
    override fun save(weather: CachedLocalWeather) {
        if (failOnSave) error("écriture impossible")
        value.value = weather
    }
    override fun clear() {
        value.value = null
    }
}

class FakeSessionExpiry(expiresAt: Long?) : SessionExpiry {
    val expiresAt = MutableStateFlow(expiresAt)
    override fun observeExpiresAt(): Flow<Long?> = expiresAt
}

/** Stockage « chiffré » en mémoire : recréer un cache dessus simule un redémarrage. */
class InMemorySecureStorage : SecureStorage {
    val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}
