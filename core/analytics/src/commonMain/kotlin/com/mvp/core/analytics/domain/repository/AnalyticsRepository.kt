package com.mvp.core.analytics.domain.repository

import com.mvp.core.analytics.domain.model.AnalyticsJournal
import com.mvp.core.common.domain.analytics.AnalyticsTracker
import kotlinx.coroutines.flow.Flow

interface AnalyticsRepository : AnalyticsTracker {

    /** Envoie tout ce qui attend (si le consentement est toujours valide). Renvoie le nombre d'événements envoyés. */
    suspend fun flush(): Int

    fun observeJournal(): Flow<AnalyticsJournal>
}
