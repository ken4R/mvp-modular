package com.mvp.feature.weather

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.location.GeoPosition
import com.mvp.core.common.domain.location.LocationAccuracy
import com.mvp.core.common.domain.location.LocationProvider
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.weather.domain.model.CachedLocalWeather
import com.mvp.feature.weather.domain.model.CurrentWeather
import com.mvp.feature.weather.domain.model.LocalWeather
import com.mvp.feature.weather.domain.model.WeatherCondition
import com.mvp.feature.weather.domain.usecase.GetLocalWeatherUseCase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalWeatherUseCaseTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)

    private var position: AppResult<GeoPosition> = AppResult.Success(GeoPosition(45.764043, 4.835659))
    private val location = object : LocationProvider {
        override suspend fun currentPosition(accuracy: LocationAccuracy) = position
        override suspend fun isAuthorized() = position is AppResult.Success
    }
    private val repository = FakeWeatherRepository()
    private val cache = InMemoryLocalWeatherCache()
    private val now = 1_000_000L
    private val getLocalWeather = GetLocalWeatherUseCase(location, repository, cache, { now }, dispatcher)

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
    fun lastWeatherIsKeptForTheWidgetWithTheRoundedPositionOnly() = scope.runTest {
        repository.weather = AppResult.Success(sunny)

        getLocalWeather(NoParams)

        assertEquals(CachedLocalWeather(LocalWeather(GeoPosition(45.76, 4.84), sunny), now), cache.value.value)
    }

    @Test
    fun aFailureKeepsThePreviousWeather() = scope.runTest {
        repository.weather = AppResult.Success(sunny)
        getLocalWeather(NoParams)
        val kept = cache.value.value

        repository.weather = AppResult.Failure(AppError.Network)
        getLocalWeather(NoParams)

        assertEquals(kept, cache.value.value)
    }

    @Test
    fun refusedPermissionForgetsTheKeptWeather() = scope.runTest {
        repository.weather = AppResult.Success(sunny)
        getLocalWeather(NoParams)

        position = AppResult.Failure(AppError.PermissionDenied)
        getLocalWeather(NoParams)

        assertEquals(null, cache.value.value)
    }

    @Test
    fun aCacheFailureDoesNotHideTheWeather() = scope.runTest {
        repository.weather = AppResult.Success(sunny)
        cache.failOnSave = true

        assertIs<AppResult.Success<LocalWeather>>(getLocalWeather(NoParams))
    }

    @Test
    fun negativeCoordinatesAreRoundedToo() = scope.runTest {
        position = AppResult.Success(GeoPosition(-33.868820, -70.209295))

        getLocalWeather(NoParams)

        assertEquals(listOf(GeoPosition(-33.87, -70.21)), repository.requests)
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
}
