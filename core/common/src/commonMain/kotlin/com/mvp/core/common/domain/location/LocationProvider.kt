package com.mvp.core.common.domain.location

import com.mvp.core.common.domain.result.AppResult

/** Position géographique en degrés décimaux (WGS 84). */
data class GeoPosition(val latitude: Double, val longitude: Double)

/**
 * Contrat entre les features qui ont besoin de la position de l'appareil (météo...) et
 * `core:location`, qui l'implémente avec l'API système de chaque plateforme.
 */
interface LocationProvider {

    /**
     * Position actuelle, approximative (de l'ordre du kilomètre).
     * Échoue avec [com.mvp.core.common.domain.error.AppError.PermissionDenied] si l'utilisateur refuse
     * la localisation (sur iOS, aussi quand elle est coupée pour tout l'appareil), et
     * [com.mvp.core.common.domain.error.AppError.Unavailable] si aucune position n'est obtenue
     * (Android : localisation coupée ; délai dépassé).
     */
    suspend fun currentPosition(): AppResult<GeoPosition>
}
