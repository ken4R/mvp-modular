package com.mvp.feature.weather.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.feature.weather.data.repository.WeatherRepositoryImpl
import com.mvp.feature.weather.data.source.OpenMeteoRemoteDataSource
import com.mvp.feature.weather.data.source.WeatherRemoteDataSource
import com.mvp.feature.weather.domain.repository.WeatherRepository
import com.mvp.feature.weather.domain.usecase.GetCurrentWeatherUseCase
import com.mvp.feature.weather.domain.usecase.GetLocalWeatherUseCase
import com.mvp.feature.weather.domain.usecase.SearchCitiesUseCase
import org.koin.dsl.module

/**
 * Nécessite le `HttpClient` fourni par `networkModule` (core:network)
 * et le `LocationProvider` fourni par `locationModule` (core:location).
 */
val weatherModule = module {
    // Data
    single<WeatherRemoteDataSource> { OpenMeteoRemoteDataSource(get()) }
    single<WeatherRepository> { WeatherRepositoryImpl(get()) }

    // Domain
    factory { SearchCitiesUseCase(get(), get<AppDispatchers>().io) }
    factory { GetCurrentWeatherUseCase(get(), get<AppDispatchers>().io) }
    factory { GetLocalWeatherUseCase(get(), get(), get<AppDispatchers>().io) }
}
