package com.mvp.core.common.domain.location

import com.mvp.core.common.domain.result.AppResult

/** Position géographique en degrés décimaux (WGS 84). */
data class GeoPosition(val latitude: Double, val longitude: Double)

/** Précision demandée à [LocationProvider.currentPosition]. */
enum class LocationAccuracy {
    /** De l'ordre du kilomètre (météo) : la permission approximative suffit. */
    Approximate,

    /** À 150 m près au plus (rappels par lieu, ADR 0023) : exige la position exacte. */
    Precise,
}

/**
 * Contrat entre les features qui ont besoin de la position de l'appareil (météo...) et
 * `core:location`, qui l'implémente avec l'API système de chaque plateforme.
 */
interface LocationProvider {

    /**
     * Position actuelle, approximative (de l'ordre du kilomètre) sauf si [accuracy] vaut [LocationAccuracy.Precise].
     * Échoue avec [com.mvp.core.common.domain.error.AppError.PermissionDenied] si l'utilisateur refuse
     * la localisation (sur iOS, aussi quand elle est coupée pour tout l'appareil ; en précis, aussi quand seule la
     * position approximative est autorisée), et
     * [com.mvp.core.common.domain.error.AppError.Unavailable] si aucune position n'est obtenue
     * (Android : localisation coupée ; délai dépassé).
     */
    suspend fun currentPosition(accuracy: LocationAccuracy = LocationAccuracy.Approximate): AppResult<GeoPosition>

    /**
     * `true` si la localisation, au moins approximative, est autorisée pour l'app en ce moment. Ne demande jamais
     * l'autorisation : sert à arrêter un usage de la position déjà obtenue quand l'utilisateur la retire (widget Météo,
     * ADR 0026).
     */
    suspend fun isAuthorized(): Boolean
}
