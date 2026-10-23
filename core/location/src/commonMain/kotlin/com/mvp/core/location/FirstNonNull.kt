package com.mvp.core.location

import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Lance toutes les [requests] en parallèle et renvoie le premier résultat non nul ; les autres sont annulées.
 * `null` si toutes répondent `null`. Sert à interroger plusieurs fournisseurs de position à la fois.
 */
internal suspend fun <T : Any> firstNonNull(requests: List<suspend () -> T?>): T? =
    channelFlow {
        requests.forEach { request -> launch { send(request()) } }
    }.filterNotNull().firstOrNull()
