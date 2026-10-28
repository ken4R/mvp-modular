package com.mvp.feature.weather

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.location.GeoPosition
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.weather.data.cache.SecureLocalWeatherCache
import com.mvp.feature.weather.data.session.WeatherUserSessionObserver
import com.mvp.feature.weather.domain.model.CachedLocalWeather
import com.mvp.feature.weather.domain.model.CurrentWeather
import com.mvp.feature.weather.domain.model.LocalWeather
import com.mvp.feature.weather.domain.model.WeatherCondition
import com.mvp.feature.weather.domain.model.WeatherWidgetState
import com.mvp.feature.weather.domain.usecase.ObserveWeatherWidgetUseCase
import com.mvp.feature.weather.domain.usecase.RefreshWeatherWidgetUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeatherWidgetUseCaseTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private var now = 1_000_000_000L
    private val cache = InMemoryLocalWeatherCache()
    private val session = FakeSessionExpiry(expiresAt = now + DAY)
    private val repository = FakeWeatherRepository()
    private val location = FakeLocationProvider()

    private val widget = ObserveWeatherWidgetUseCase(cache, session, { now }, dispatcher)
    private val refresh = RefreshWeatherWidgetUseCase(repository, cache, session, location, { now }, dispatcher)

    private val lyon = GeoPosition(45.76, 4.84)
    private val sunny = CurrentWeather(18.4, 17.5, 40, 6.0, WeatherCondition.Clear, isDay = true)
    private val rainy = CurrentWeather(11.0, 9.0, 90, 20.0, WeatherCondition.Rain, isDay = false)

    private suspend fun current(): WeatherWidgetState = (widget(NoParams).first() as AppResult.Success).data

    @Test
    fun showsTheLastWeatherUntilItIsTooOld() = scope.runTest {
        cache.save(CachedLocalWeather(LocalWeather(lyon, sunny), fetchedAt = now))

        assertEquals(
            WeatherWidgetState.Weather(
                temperatureCelsius = 18.4,
                condition = WeatherCondition.Clear,
                isDay = true,
                updatedAt = now,
                visibleUntil = now + ObserveWeatherWidgetUseCase.MAX_AGE_MS,
                link = "mvpmodular://weather",
            ),
            current(),
        )
        now += ObserveWeatherWidgetUseCase.MAX_AGE_MS
        assertEquals(WeatherWidgetState.NoData, current())
    }

    @Test
    fun noDataUntilTheAppHasFetchedTheWeather() = scope.runTest {
        assertEquals(WeatherWidgetState.NoData, current())
    }

    @Test
    fun nothingIsShownWithoutAValidSession() = scope.runTest {
        cache.save(CachedLocalWeather(LocalWeather(lyon, sunny), fetchedAt = now))
        session.expiresAt.value = null
        assertEquals(WeatherWidgetState.SignedOut, current())

        session.expiresAt.value = now
        assertEquals(WeatherWidgetState.SignedOut, current())
    }

    @Test
    fun visibilityStopsAtTheEndOfTheSessionIfItComesFirst() = scope.runTest {
        session.expiresAt.value = now + HOUR
        cache.save(CachedLocalWeather(LocalWeather(lyon, sunny), fetchedAt = now))

        assertEquals(now + HOUR, assertIs<WeatherWidgetState.Weather>(current()).visibleUntil)
    }

    @Test
    fun widgetIsEmptiedWhenTheWeatherGetsTooOldWhileObserved() = scope.runTest {
        cache.save(CachedLocalWeather(LocalWeather(lyon, sunny), fetchedAt = now))
        val states = mutableListOf<WeatherWidgetState>()
        val job = launch { widget(NoParams).collect { states += (it as AppResult.Success).data } }
        testScheduler.runCurrent()

        val maxAge = ObserveWeatherWidgetUseCase.MAX_AGE_MS
        now += maxAge
        testScheduler.advanceTimeBy(maxAge + 1)
        testScheduler.runCurrent()
        job.cancel()

        assertIs<WeatherWidgetState.Weather>(states.first())
        assertEquals(WeatherWidgetState.NoData, states.last())
    }

    @Test
    fun refreshFetchesTheKeptPositionWithoutAskingForTheLocation() = scope.runTest {
        cache.save(CachedLocalWeather(LocalWeather(lyon, sunny), fetchedAt = now))
        now += RefreshWeatherWidgetUseCase.MIN_INTERVAL_MS
        repository.weather = AppResult.Success(rainy)

        assertEquals(AppResult.Success(Unit), refresh(NoParams))

        assertEquals(listOf(lyon), repository.requests)
        assertEquals(CachedLocalWeather(LocalWeather(lyon, rainy), fetchedAt = now), cache.value.value)
    }

    @Test
    fun refreshDoesNothingWhenTheWeatherIsRecentOrMissing() = scope.runTest {
        refresh(NoParams)
        cache.save(CachedLocalWeather(LocalWeather(lyon, sunny), fetchedAt = now))
        now += RefreshWeatherWidgetUseCase.MIN_INTERVAL_MS - 1
        refresh(NoParams)

        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun refreshDoesNothingWithoutSession() = scope.runTest {
        cache.save(CachedLocalWeather(LocalWeather(lyon, sunny), fetchedAt = now))
        now += HOUR
        session.expiresAt.value = null

        refresh(NoParams)

        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun withdrawnLocationPermissionForgetsTheWeatherWithoutSendingThePosition() = scope.runTest {
        cache.save(CachedLocalWeather(LocalWeather(lyon, sunny), fetchedAt = now))
        now += HOUR
        location.authorized = false

        assertEquals(AppResult.Success(Unit), refresh(NoParams))

        assertTrue(repository.requests.isEmpty())
        assertNull(cache.value.value)
    }

    @Test
    fun aNewerWeatherWrittenDuringTheRefreshIsKept() = scope.runTest {
        cache.save(CachedLocalWeather(LocalWeather(lyon, sunny), fetchedAt = now))
        now += HOUR
        val paris = CachedLocalWeather(LocalWeather(GeoPosition(48.86, 2.35), sunny), fetchedAt = now)
        repository.weather = AppResult.Success(rainy)
        repository.duringRequest = { cache.save(paris) }

        refresh(NoParams)

        assertEquals(paris, cache.value.value)
    }

    @Test
    fun aLogoutDuringTheRefreshIsNotUndone() = scope.runTest {
        cache.save(CachedLocalWeather(LocalWeather(lyon, sunny), fetchedAt = now))
        now += HOUR
        repository.weather = AppResult.Success(rainy)
        repository.duringRequest = { cache.clear() }

        refresh(NoParams)

        assertNull(cache.value.value)
    }

    @Test
    fun aFailedRefreshKeepsThePreviousWeather() = scope.runTest {
        val kept = CachedLocalWeather(LocalWeather(lyon, sunny), fetchedAt = now)
        cache.save(kept)
        now += HOUR
        repository.weather = AppResult.Failure(AppError.Network)

        assertEquals(AppResult.Failure(AppError.Network), refresh(NoParams))
        assertEquals(kept, cache.value.value)
    }

    @Test
    fun secureCacheSurvivesARestartAndIsClearedAtLogout() = scope.runTest {
        val storage = InMemorySecureStorage()
        val kept = CachedLocalWeather(LocalWeather(lyon, sunny), fetchedAt = now)
        SecureLocalWeatherCache(storage).save(kept)

        val restarted = SecureLocalWeatherCache(storage)
        assertEquals(kept, restarted.observe().first())

        assertEquals(AppResult.Success(Unit), WeatherUserSessionObserver(restarted).clearUserData())
        assertNull(restarted.observe().first())
        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun unreadableStoredWeatherCountsAsMissing() = scope.runTest {
        val storage = InMemorySecureStorage()
        storage.values["weather.widget.last_local"] = "{pas du json"

        assertNull(SecureLocalWeatherCache(storage).observe().first())
    }

    private companion object {
        const val HOUR = 60 * 60 * 1000L
        const val DAY = 24 * HOUR
    }
}
