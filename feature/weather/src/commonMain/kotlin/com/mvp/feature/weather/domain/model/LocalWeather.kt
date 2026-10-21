package com.mvp.feature.weather.domain.model

import com.mvp.core.common.domain.location.GeoPosition

/** Météo actuelle à la position de l'appareil. */
data class LocalWeather(
    /** Position arrondie, celle qui a été envoyée au service météo. */
    val position: GeoPosition,
    val weather: CurrentWeather,
)
