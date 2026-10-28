package com.mvp.feature.weather.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.common.domain.session.UserSessionObserver
import com.mvp.feature.weather.data.cache.SecureLocalWeatherCache
import com.mvp.feature.weather.data.repository.WeatherRepositoryImpl
import com.mvp.feature.weather.data.source.OpenMeteoRemoteDataSource
import com.mvp.feature.weather.data.session.WeatherUserSessionObserver
import com.mvp.feature.weather.data.source.WeatherRemoteDataSource
import com.mvp.feature.weather.domain.repository.LocalWeatherCache
import com.mvp.feature.weather.domain.repository.WeatherRepository
import com.mvp.feature.weather.domain.usecase.GetCurrentWeatherUseCase
import com.mvp.feature.weather.domain.usecase.GetLocalWeatherUseCase
import com.mvp.feature.weather.domain.usecase.ObserveWeatherWidgetUseCase
import com.mvp.feature.weather.domain.usecase.RefreshWeatherWidgetUseCase
import com.mvp.feature.weather.domain.usecase.SearchCitiesUseCase
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private val systemNow: () -> Long = { Clock.System.now().toEpochMilliseconds() }

/**
 * Nécessite le `HttpClient` fourni par `networkModule` (core:network)
 * et le `LocationProvider` fourni par `locationModule` (core:location).
 * Widget Météo (ADR 0026) : `SecureStorage` de core:securestorage, `SessionExpiry` de feature:auth.
 */
val weatherModule = module {
    // Data
    single<WeatherRemoteDataSource> { OpenMeteoRemoteDataSource(get()) }
    single<WeatherRepository> { WeatherRepositoryImpl(get()) }
    // Dernière météo « Ma position » pour le widget, chiffrée, effacée à la déconnexion.
    single<LocalWeatherCache> { SecureLocalWeatherCache(get()) }
    single { WeatherUserSessionObserver(get()) } bind UserSessionObserver::class

    // Domain
    factory { SearchCitiesUseCase(get(), get<AppDispatchers>().io) }
    factory { GetCurrentWeatherUseCase(get(), get<AppDispatchers>().io) }
    factory { GetLocalWeatherUseCase(get(), get(), get(), systemNow, get<AppDispatchers>().io) }
    factory { ObserveWeatherWidgetUseCase(get(), get(), systemNow, get<AppDispatchers>().io) }
    factory { RefreshWeatherWidgetUseCase(get(), get(), get(), systemNow, get<AppDispatchers>().io) }
}
