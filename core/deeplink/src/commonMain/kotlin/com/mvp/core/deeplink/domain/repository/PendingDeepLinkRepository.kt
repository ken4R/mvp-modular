package com.mvp.core.deeplink.domain.repository

import com.mvp.core.deeplink.domain.model.DeepLink
import kotlinx.coroutines.flow.Flow

/**
 * Le lien reçu en dernier, en attente d'être affiché : l'app ne l'affiche qu'une fois la connexion faite
 * et le verrou levé. Un seul lien attend à la fois : le plus récent remplace le précédent.
 */
interface PendingDeepLinkRepository {
    fun observe(): Flow<DeepLink?>
    fun set(link: DeepLink)

    /** Retire [link] s'il est toujours celui en attente (un lien arrivé entre-temps est gardé). */
    fun consume(link: DeepLink)
    fun clear()
}
