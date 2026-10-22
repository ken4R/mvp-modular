package com.mvp.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.mvp.core.common.domain.connectivity.ConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * Réseau par défaut du système, suivi par `ConnectivityManager` (permission ACCESS_NETWORK_STATE, déclarée
 * par l'app). En ligne = réseau avec accès internet **validé** par le système : un Wi-Fi sans internet ou
 * derrière un portail captif compte comme hors ligne. Un seul callback système, partagé par tous les abonnés
 * (Android limite le nombre de callbacks par app), retiré quelques secondes après le dernier.
 */
internal class AndroidConnectivityObserver(context: Context) : ConnectivityObserver {

    private val manager = context.getSystemService(ConnectivityManager::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val isOnline: StateFlow<Boolean> by lazy { systemCallbacks().stateIn(scope, SharingStarted.WhileSubscribed(STOP_DELAY_MILLIS), isOnlineNow()) }

    override fun observeIsOnline(): Flow<Boolean> = isOnline

    private fun systemCallbacks(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.isOnline())
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        // État de départ : le callback ne se manifeste qu'au premier réseau disponible.
        trySend(isOnlineNow())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun isOnlineNow(): Boolean =
        manager.activeNetwork?.let(manager::getNetworkCapabilities)?.isOnline() ?: false
}

private const val STOP_DELAY_MILLIS = 5_000L

private fun NetworkCapabilities.isOnline(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
