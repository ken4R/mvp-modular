package com.mvp.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.location.GeoPosition
import com.mvp.core.common.domain.location.LocationProvider
import com.mvp.core.common.domain.result.AppResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Position via le `LocationManager` du système, sans Google Play Services.
 * La permission (ACCESS_COARSE_LOCATION suffit) est demandée par l'écran Météo ; ici on vérifie seulement qu'elle est accordée.
 */
internal class AndroidLocationProvider(private val context: Context) : LocationProvider {

    override suspend fun currentPosition(): AppResult<GeoPosition> {
        if (!isGranted(Manifest.permission.ACCESS_COARSE_LOCATION) && !isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return AppResult.Failure(AppError.PermissionDenied)
        }
        val manager = context.getSystemService(LocationManager::class.java)
            ?: return AppResult.Failure(AppError.Unavailable)
        // Aucun fournisseur actif : la localisation est coupée dans les paramètres.
        val provider = bestProvider(manager) ?: return AppResult.Failure(AppError.Unavailable)
        return try {
            val location = recentLastKnown(manager, provider)
                ?: withTimeoutOrNull(LOCATION_TIMEOUT_MS) { requestCurrent(manager, provider) }
            if (location == null) {
                AppResult.Failure(AppError.Unavailable)
            } else {
                AppResult.Success(GeoPosition(location.latitude, location.longitude))
            }
        } catch (e: SecurityException) {
            // Permission retirée entre la vérification et la demande.
            AppResult.Failure(AppError.PermissionDenied)
        }
    }

    private fun isGranted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Fournisseur « fused » (Android 12+) ou réseau en priorité : une position approximative suffit.
     * Avant Android 12, le GPS demande la permission de localisation précise, que l'app ne réclame pas.
     */
    private fun bestProvider(manager: LocationManager): String? {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                add(LocationManager.GPS_PROVIDER)
            }
        }
        val available = manager.allProviders
        return candidates.firstOrNull { it in available && manager.isProviderEnabled(it) }
    }

    /** Dernière position connue si elle a moins de [MAX_LAST_KNOWN_AGE_MS] : évite d'attendre une nouvelle mesure. */
    @SuppressLint("MissingPermission")
    private fun recentLastKnown(manager: LocationManager, provider: String): Location? {
        val location = manager.getLastKnownLocation(provider) ?: return null
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        return location.takeIf { ageMs in 0..MAX_LAST_KNOWN_AGE_MS }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrent(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } else {
                val listener = SingleUpdateListener { location -> if (continuation.isActive) continuation.resume(location) }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }

    /**
     * Android 8 à 10. Toutes les méthodes sont implémentées : leurs versions par défaut n'existent
     * qu'à partir d'Android 11, un appel à l'une d'elles planterait sinon.
     */
    private class SingleUpdateListener(private val onLocation: (Location?) -> Unit) : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)
        override fun onProviderDisabled(provider: String) = onLocation(null)
        override fun onProviderEnabled(provider: String) = Unit

        @Deprecated("Remplacée par rien côté système, conservée pour Android < 11")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private companion object {
        const val MAX_LAST_KNOWN_AGE_MS = 10 * 60 * 1000L
    }
}
