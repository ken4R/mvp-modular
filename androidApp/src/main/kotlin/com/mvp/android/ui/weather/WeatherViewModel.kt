package com.mvp.android.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.onFailure
import com.mvp.core.common.domain.result.onSuccess
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.weather.domain.model.City
import com.mvp.feature.weather.domain.model.CurrentWeather
import com.mvp.feature.weather.domain.usecase.GetCurrentWeatherUseCase
import com.mvp.feature.weather.domain.usecase.GetLocalWeatherUseCase
import com.mvp.feature.weather.domain.usecase.SearchCitiesUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lieu dont on affiche la météo : une ville choisie, ou la position de l'appareil. */
sealed interface WeatherPlace {
    data class OfCity(val city: City) : WeatherPlace
    data object MyPosition : WeatherPlace
}

sealed interface WeatherContent {
    data object Idle : WeatherContent
    data object Loading : WeatherContent
    data class Results(val cities: List<City>) : WeatherContent
    data class Weather(val place: WeatherPlace, val weather: CurrentWeather) : WeatherContent

    /** [canOpenSettings] : la localisation est refusée, l'écran propose d'ouvrir les paramètres de l'app. */
    data class Error(val message: String, val canOpenSettings: Boolean = false) : WeatherContent
}

data class WeatherUiState(
    val query: String = "",
    val content: WeatherContent = WeatherContent.Idle,
)

class WeatherViewModel(
    private val searchCities: SearchCitiesUseCase,
    private val getCurrentWeather: GetCurrentWeatherUseCase,
    private val getLocalWeather: GetLocalWeatherUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(WeatherUiState())
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    /** Une seule requête à la fois : la dernière action de l'utilisateur l'emporte. */
    private var request: Job? = null

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun onSearch() = load {
        searchCities(_state.value.query)
            .onSuccess { cities -> show(WeatherContent.Results(cities)) }
            .onFailure(::showError)
    }

    fun onSelect(city: City) = load {
        getCurrentWeather(city)
            .onSuccess { weather -> show(WeatherContent.Weather(WeatherPlace.OfCity(city), weather)) }
            .onFailure(::showError)
    }

    /** À appeler une fois la permission de localisation accordée. */
    fun onLocate() = load {
        getLocalWeather(NoParams)
            .onSuccess { local -> show(WeatherContent.Weather(WeatherPlace.MyPosition, local.weather)) }
            .onFailure(::showError)
    }

    fun onLocationPermissionDenied() {
        request?.cancel()
        showError(AppError.PermissionDenied)
    }

    fun onRefresh() {
        val content = _state.value.content as? WeatherContent.Weather ?: return
        when (val place = content.place) {
            is WeatherPlace.OfCity -> onSelect(place.city)
            WeatherPlace.MyPosition -> onLocate()
        }
    }

    private fun load(block: suspend () -> Unit) {
        request?.cancel()
        show(WeatherContent.Loading)
        request = viewModelScope.launch { block() }
    }

    private fun show(content: WeatherContent) = _state.update { it.copy(content = content) }

    private fun showError(error: AppError) =
        show(WeatherContent.Error(error.toMessage(), canOpenSettings = error == AppError.PermissionDenied))
}

private fun AppError.toMessage(): String = when (this) {
    is AppError.Validation -> message
    AppError.NotFound -> "Ville introuvable"
    AppError.Network -> "Problème de connexion"
    AppError.Unauthorized -> "Accès refusé"
    AppError.PermissionDenied -> "La localisation est refusée. Autorisez-la dans les paramètres Android pour voir la météo à votre position."
    AppError.Unavailable -> "Position indisponible. Vérifiez que la localisation est activée, puis réessayez."
    // Le détail technique d'une exception n'est jamais montré à l'utilisateur.
    is AppError.Unknown -> "Erreur inattendue"
}
