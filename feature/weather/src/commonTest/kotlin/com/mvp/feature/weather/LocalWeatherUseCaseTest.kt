package com.mvp.feature.weather

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.location.GeoPosition
import com.mvp.core.common.domain.location.LocationProvider
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.weather.domain.model.City
import com.mvp.feature.weather.domain.model.CurrentWeather
import com.mvp.feature.weather.domain.model.LocalWeather
import com.mvp.feature.weather.domain.model.WeatherCondition
import com.mvp.feature.weather.domain.repository.WeatherRepository
import com.mvp.feature.weather.domain.usecase.GetLocalWeatherUseCase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalWeatherUseCaseTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)

    private var position: AppResult<GeoPosition> = AppResult.Success(GeoPosition(45.764043, 4.835659))
    private val location = object : LocationProvider {
        override suspend fun currentPosition() = position
    }
    private val repository = FakeWeatherRepository()
    private val getLocalWeather = GetLocalWeatherUseCase(location, repository, dispatcher)

    private val sunny = CurrentWeather(18.0, 17.5, 40, 6.0, WeatherCondition.Clear, isDay = true)

    @Test
    fun weatherIsFetchedForThePositionRoundedToTwoDecimals() = scope.runTest {
        repository.weather = AppResult.Success(sunny)

        val result = getLocalWeather(NoParams)

        val rounded = GeoPosition(45.76, 4.84)
        assertEquals(AppResult.Success(LocalWeather(rounded, sunny)), result)
        assertEquals(listOf(rounded), repository.requests)
    }

    @Test
    fun negativeCoordinatesAreRoundedToo() = scope.runTest {
        position = AppResult.Success(GeoPosition(-33.868820, -151.209295))

        getLocalWeather(NoParams)

        assertEquals(listOf(GeoPosition(-33.87, -151.21)), repository.requests)
    }

    @Test
    fun refusedPermissionIsReportedWithoutCallingTheServer() = scope.runTest {
        position = AppResult.Failure(AppError.PermissionDenied)

        assertEquals(AppResult.Failure(AppError.PermissionDenied), getLocalWeather(NoParams))
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun unavailablePositionIsReportedWithoutCallingTheServer() = scope.runTest {
        position = AppResult.Failure(AppError.Unavailable)

        assertEquals(AppResult.Failure(AppError.Unavailable), getLocalWeather(NoParams))
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun weatherServiceErrorIsReported() = scope.runTest {
        repository.weather = AppResult.Failure(AppError.Network)

        assertEquals(AppResult.Failure(AppError.Network), getLocalWeather(NoParams))
    }

    private class FakeWeatherRepository : WeatherRepository {
        var weather: AppResult<CurrentWeather> = AppResult.Failure(AppError.Unknown())
        val requests = mutableListOf<GeoPosition>()

        override suspend fun searchCities(query: String): AppResult<List<City>> = AppResult.Success(emptyList())

        override suspend fun getCurrentWeather(latitude: Double, longitude: Double): AppResult<CurrentWeather> {
            requests += GeoPosition(latitude, longitude)
            return weather
        }
    }
}
