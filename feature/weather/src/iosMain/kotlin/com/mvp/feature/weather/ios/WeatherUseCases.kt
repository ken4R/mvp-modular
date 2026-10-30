package com.mvp.feature.weather.ios

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.ios.Subscription
import com.mvp.core.common.ios.SwiftUseCases
import com.mvp.feature.weather.domain.model.WeatherWidgetState
import com.mvp.feature.weather.domain.usecase.GetCurrentWeatherUseCase
import com.mvp.feature.weather.domain.usecase.GetLocalWeatherUseCase
import com.mvp.feature.weather.domain.usecase.ObserveWeatherWidgetUseCase
import com.mvp.feature.weather.domain.usecase.RefreshWeatherWidgetUseCase
import com.mvp.feature.weather.domain.usecase.SearchCitiesUseCase
import org.koin.core.component.get

/**
 * Point d'accès Swift aux use cases de la météo.
 * Koin n'est pas utilisable directement depuis Swift. Les use cases suspend s'appellent en `async` côté Swift ;
 * les Flow passent par des callbacks (voir SwiftUseCases).
 */
object WeatherUseCases : SwiftUseCases() {
    fun searchCities(): SearchCitiesUseCase = get()
    fun getCurrentWeather(): GetCurrentWeatherUseCase = get()
    fun getLocalWeather(): GetLocalWeatherUseCase = get()
    fun refreshWeatherWidget(): RefreshWeatherWidgetUseCase = get()

    /**
     * Contenu du widget Météo (ADR 0026), à recopier dans le fichier partagé avec l'extension WidgetKit. En cas
     * d'erreur, n'y écrire aucune météo. Les callbacks sont appelés sur le main thread.
     */
    fun observeWeatherWidget(onState: (WeatherWidgetState) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveWeatherWidgetUseCase>()(NoParams).subscribe(onState, onError)
}
