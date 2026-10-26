@file:OptIn(ExperimentalForeignApi::class)

package com.mvp.core.location

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.location.GeoPosition
import com.mvp.core.common.domain.location.LocationAccuracy
import com.mvp.core.common.domain.location.LocationProvider
import com.mvp.core.common.domain.result.AppResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLAccuracyAuthorization
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * Position via CoreLocation. Demande l'autorisation « Lorsque l'app est active » la première fois
 * (le texte affiché est `NSLocationWhenInUseUsageDescription` dans l'Info.plist de l'app).
 * Précision du kilomètre par défaut : suffisante pour la météo, et iOS n'a pas besoin du GPS. En précis (rappels par
 * lieu, ADR 0023), la meilleure précision, et la « Position exacte » doit être autorisée.
 */
internal class CoreLocationProvider : LocationProvider {

    // CLLocationManager doit vivre sur un thread avec une run loop : le main thread.
    override suspend fun currentPosition(accuracy: LocationAccuracy): AppResult<GeoPosition> = withContext(Dispatchers.Main) {
        val delegate = LocationDelegate(precise = accuracy == LocationAccuracy.Precise)
        val manager = CLLocationManager()
        manager.delegate = delegate
        manager.desiredAccuracy = if (accuracy == LocationAccuracy.Precise) kCLLocationAccuracyBest else kCLLocationAccuracyKilometer
        try {
            locate(manager, delegate, accuracy)
        } finally {
            manager.stopUpdatingLocation()
            // Le délégué est une référence faible côté iOS : on le garde jusqu'ici, puis on le détache.
            manager.delegate = null
        }
    }

    private suspend fun locate(
        manager: CLLocationManager,
        delegate: LocationDelegate,
        accuracy: LocationAccuracy,
    ): AppResult<GeoPosition> {
        var status = manager.authorizationStatus
        if (status == kCLAuthorizationStatusNotDetermined) {
            manager.requestWhenInUseAuthorization()
            status = delegate.authorization.first { it != kCLAuthorizationStatusNotDetermined }
        }
        // Refusé, restreint (contrôle parental) ou service de localisation coupé pour tout l'appareil :
        // iOS renvoie « refusé » dans ce dernier cas aussi, sans le distinguer.
        if (status != kCLAuthorizationStatusAuthorizedWhenInUse && status != kCLAuthorizationStatusAuthorizedAlways) {
            return AppResult.Failure(AppError.PermissionDenied)
        }
        // Position approximative seulement (choix par défaut de l'app, NSLocationDefaultAccuracyReduced) : iOS ne
        // donnerait qu'une position à quelques kilomètres près.
        if (accuracy == LocationAccuracy.Precise && manager.accuracyAuthorization != CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy) {
            return AppResult.Failure(AppError.PermissionDenied)
        }
        manager.requestLocation()
        // Le délai ne porte que sur la mesure : l'utilisateur prend le temps qu'il veut pour répondre à la demande d'autorisation.
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) { delegate.result.await() }
            ?: AppResult.Failure(AppError.Unavailable)
    }
}

private class LocationDelegate(private val precise: Boolean) : NSObject(), CLLocationManagerDelegateProtocol {
    val authorization = MutableStateFlow<CLAuthorizationStatus>(kCLAuthorizationStatusNotDetermined)
    val result = CompletableDeferred<AppResult<GeoPosition>>()

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        authorization.value = manager.authorizationStatus
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
        // En précis, une mesure trop large ne doit pas poser un rappel au mauvais endroit (ADR 0023).
        if (precise && (location.horizontalAccuracy < 0 || location.horizontalAccuracy > PRECISE_MAX_ACCURACY_METERS)) {
            result.complete(AppResult.Failure(AppError.Unavailable))
            return
        }
        val position = location.coordinate.useContents { GeoPosition(latitude, longitude) }
        result.complete(AppResult.Success(position))
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        val error = if (didFailWithError.code == CL_ERROR_DENIED) AppError.PermissionDenied else AppError.Unavailable
        result.complete(AppResult.Failure(error))
    }
}

/** `kCLErrorDenied` (domaine `kCLErrorDomain`) : l'autorisation a été retirée pendant la demande. */
private const val CL_ERROR_DENIED = 1L
