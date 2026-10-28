package com.mvp.feature.weather.data.cache

import com.mvp.core.common.domain.location.GeoPosition
import com.mvp.core.securestorage.SecureStorage
import com.mvp.feature.weather.domain.model.CachedLocalWeather
import com.mvp.feature.weather.domain.model.CurrentWeather
import com.mvp.feature.weather.domain.model.LocalWeather
import com.mvp.feature.weather.domain.model.WeatherCondition
import com.mvp.feature.weather.domain.repository.LocalWeatherCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Météo gardée dans le stockage chiffré (Keystore / Keychain) : elle contient une position, même arrondie.
 * Lue au premier accès puis gardée en mémoire ; une valeur illisible (autre version) compte comme absente.
 */
internal class SecureLocalWeatherCache(private val storage: SecureStorage) : LocalWeatherCache {

    private val cached by lazy { MutableStateFlow(read()) }

    // Première lecture au moment de la collecte (sur le dispatcher io), pas à la création du Flow.
    override fun observe(): Flow<CachedLocalWeather?> = flow { emitAll(cached) }

    override fun save(weather: CachedLocalWeather) {
        storage.put(KEY, json.encodeToString(StoredWeather.serializer(), StoredWeather.of(weather)))
        cached.value = weather
    }

    override fun clear() {
        cached.value = null
        storage.remove(KEY)
    }

    private fun read(): CachedLocalWeather? = storage.get(KEY)?.let { value ->
        runCatching { json.decodeFromString(StoredWeather.serializer(), value).toDomain() }.getOrNull()
    }

    private companion object {
        // Clé stockée sur l'appareil : ne pas la renommer sans migration.
        const val KEY = "weather.widget.last_local"
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class StoredWeather(
    @SerialName("lat") val latitude: Double,
    @SerialName("lon") val longitude: Double,
    @SerialName("t") val temperature: Double,
    @SerialName("feels") val feelsLike: Double,
    @SerialName("hum") val humidity: Int,
    @SerialName("wind") val windSpeed: Double,
    // Nom Kotlin de la condition : une condition inconnue d'une autre version devient Unknown.
    @SerialName("cond") val condition: String,
    @SerialName("day") val isDay: Boolean,
    @SerialName("at") val fetchedAt: Long,
) {
    fun toDomain() = CachedLocalWeather(
        local = LocalWeather(
            position = GeoPosition(latitude, longitude),
            weather = CurrentWeather(
                temperatureCelsius = temperature,
                feelsLikeCelsius = feelsLike,
                humidityPercent = humidity,
                windSpeedKmh = windSpeed,
                condition = WeatherCondition.entries.firstOrNull { it.name == condition } ?: WeatherCondition.Unknown,
                isDay = isDay,
            ),
        ),
        fetchedAt = fetchedAt,
    )

    companion object {
        fun of(cached: CachedLocalWeather) = with(cached.local) {
            StoredWeather(
                latitude = position.latitude,
                longitude = position.longitude,
                temperature = weather.temperatureCelsius,
                feelsLike = weather.feelsLikeCelsius,
                humidity = weather.humidityPercent,
                windSpeed = weather.windSpeedKmh,
                condition = weather.condition.name,
                isDay = weather.isDay,
                fetchedAt = cached.fetchedAt,
            )
        }
    }
}
