package com.mvp.core.deeplink.data

import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.core.deeplink.domain.repository.PendingDeepLinkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** En mémoire seulement : un lien n'a de sens que pour l'ouverture en cours de l'app. */
internal class InMemoryPendingDeepLinkRepository : PendingDeepLinkRepository {
    private val pending = MutableStateFlow<DeepLink?>(null)

    override fun observe(): Flow<DeepLink?> = pending.asStateFlow()

    override fun set(link: DeepLink) {
        pending.value = link
    }

    override fun consume(link: DeepLink) = pending.update { current -> if (current == link) null else current }

    override fun clear() {
        pending.value = null
    }
}
