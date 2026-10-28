package com.mvp.feature.weather.domain.model

/**
 * Dernière météo « Ma position » obtenue par l'app, gardée pour le widget Météo (ADR 0026). [fetchedAt] en epoch ms.
 * La position est celle, arrondie à environ 1 km, déjà envoyée au service météo.
 */
data class CachedLocalWeather(
    val local: LocalWeather,
    val fetchedAt: Long,
)

/**
 * Ce que le widget Météo de l'écran d'accueil peut afficher. Il ne montre que la dernière météo obtenue par l'app
 * (le widget ne demande jamais la position), pas plus de [com.mvp.feature.weather.domain.usecase.ObserveWeatherWidgetUseCase.MAX_AGE_MS] après, et
 * seulement avec une session valide : la position gardée appartient à l'utilisateur connecté.
 */
sealed interface WeatherWidgetState {

    /** Pas de session valide : le widget invite à se connecter. */
    data object SignedOut : WeatherWidgetState

    /** Pas de météo récente : le widget invite à ouvrir l'onglet Météo. */
    data object NoData : WeatherWidgetState

    /**
     * [updatedAt] : heure de la mesure (epoch ms). [visibleUntil] (epoch ms) : au-delà, la météo est trop ancienne
     * ou la session finie, et le widget ne doit plus la montrer même si l'app n'a pas tourné.
     */
    data class Weather(
        val temperatureCelsius: Double,
        val condition: WeatherCondition,
        val isDay: Boolean,
        val updatedAt: Long,
        val visibleUntil: Long,
        /** Lien qui ouvre l'onglet Météo. */
        val link: String,
    ) : WeatherWidgetState
}
