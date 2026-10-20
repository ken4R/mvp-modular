package com.mvp.feature.weather

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.network.createHttpClient
import com.mvp.feature.weather.data.repository.WeatherRepositoryImpl
import com.mvp.feature.weather.data.repository.weatherConditionOf
import com.mvp.feature.weather.data.source.OpenMeteoRemoteDataSource
import com.mvp.feature.weather.domain.model.City
import com.mvp.feature.weather.domain.model.CurrentWeather
import com.mvp.feature.weather.domain.model.WeatherCondition
import com.mvp.feature.weather.domain.usecase.GetCurrentWeatherUseCase
import com.mvp.feature.weather.domain.usecase.SearchCitiesUseCase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WeatherUseCasesTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val requests = mutableListOf<HttpRequestData>()

    /** Le serveur Open-Meteo est simulé par un MockEngine qui répond avec [handler]. */
    private var handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondJson("{}") }

    private val repository = WeatherRepositoryImpl(
        OpenMeteoRemoteDataSource(
            createHttpClient(MockEngine { request -> requests += request; handler(this, request) }),
        ),
    )
    private val searchCities = SearchCitiesUseCase(repository, dispatcher)
    private val getCurrentWeather = GetCurrentWeatherUseCase(repository, dispatcher)

    private val lyon = City(2996944, "Lyon", "Auvergne-Rhône-Alpes", "France", 45.75, 4.85)

    @Test
    fun searchTrimsQueryAndMapsCities() = scope.runTest {
        handler = { respondJson(GEOCODING_LYON) }

        val result = searchCities("  Lyon ")

        assertEquals(AppResult.Success(listOf(lyon)), result)
        val url = requests.single().url
        assertEquals("geocoding-api.open-meteo.com", url.host)
        assertEquals("Lyon", url.parameters["name"])
    }

    @Test
    fun searchWithoutMatchReturnsEmptyList() = scope.runTest {
        handler = { respondJson("""{"generationtime_ms": 0.5}""") }

        assertEquals(AppResult.Success(emptyList()), searchCities("Xyzzy"))
    }

    @Test
    fun tooShortQueryIsRejectedWithoutCallingTheServer() = scope.runTest {
        val result = searchCities(" L ")

        assertIs<AppError.Validation>((result as AppResult.Failure).error)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun currentWeatherIsFetchedForCityCoordinates() = scope.runTest {
        handler = { respondJson(FORECAST_RAIN) }

        val result = getCurrentWeather(lyon)

        val expected = CurrentWeather(
            temperatureCelsius = 12.4,
            feelsLikeCelsius = 10.9,
            humidityPercent = 87,
            windSpeedKmh = 14.2,
            condition = WeatherCondition.Rain,
            isDay = true,
        )
        assertEquals(AppResult.Success(expected), result)
        val url = requests.single().url
        assertEquals("api.open-meteo.com", url.host)
        assertEquals("45.75", url.parameters["latitude"])
        assertEquals("4.85", url.parameters["longitude"])
    }

    @Test
    fun serverErrorIsReportedAsUnknown() = scope.runTest {
        handler = { respondError(HttpStatusCode.InternalServerError) }

        assertIs<AppError.Unknown>((getCurrentWeather(lyon) as AppResult.Failure).error)
    }

    @Test
    fun malformedResponseIsReportedAsUnknown() = scope.runTest {
        handler = { respondJson("""{"current": {}}""") }

        assertIs<AppError.Unknown>((getCurrentWeather(lyon) as AppResult.Failure).error)
    }

    @Test
    fun connectionFailureIsReportedAsNetwork() = scope.runTest {
        handler = { throw ConnectionLostException() }

        assertEquals(AppResult.Failure(AppError.Network), searchCities("Lyon"))
    }

    @Test
    fun wmoCodesAreMappedToConditions() {
        assertEquals(WeatherCondition.Clear, weatherConditionOf(0))
        assertEquals(WeatherCondition.PartlyCloudy, weatherConditionOf(2))
        assertEquals(WeatherCondition.Cloudy, weatherConditionOf(3))
        assertEquals(WeatherCondition.Fog, weatherConditionOf(45))
        assertEquals(WeatherCondition.Drizzle, weatherConditionOf(53))
        assertEquals(WeatherCondition.Rain, weatherConditionOf(81))
        assertEquals(WeatherCondition.Snow, weatherConditionOf(86))
        assertEquals(WeatherCondition.Thunderstorm, weatherConditionOf(95))
        assertEquals(WeatherCondition.Unknown, weatherConditionOf(42))
    }

    private fun MockRequestHandleScope.respondJson(json: String) =
        respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private class ConnectionLostException : Exception("connexion perdue")

    private companion object {
        val GEOCODING_LYON = """
            {
              "results": [
                {
                  "id": 2996944, "name": "Lyon", "latitude": 45.75, "longitude": 4.85,
                  "elevation": 173.0, "country_code": "FR", "timezone": "Europe/Paris",
                  "country": "France", "admin1": "Auvergne-Rhône-Alpes"
                }
              ],
              "generationtime_ms": 0.9
            }
        """.trimIndent()

        val FORECAST_RAIN = """
            {
              "latitude": 45.76, "longitude": 4.84, "timezone": "Europe/Paris",
              "current": {
                "time": "2026-10-20T14:30", "interval": 900,
                "temperature_2m": 12.4, "apparent_temperature": 10.9,
                "relative_humidity_2m": 87, "wind_speed_10m": 14.2,
                "weather_code": 61, "is_day": 1
              }
            }
        """.trimIndent()
    }
}
