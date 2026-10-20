package com.mvp.feature.weather.domain.model

data class City(
    val id: Long,
    val name: String,
    /** Région ou état, si connu (ex. « Auvergne-Rhône-Alpes »). */
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
)
