package com.mvp.core.crashreporting.domain.repository

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.crashreporting.domain.model.CrashReport
import kotlinx.coroutines.flow.Flow

interface CrashReportRepository {

    /**
     * Branche le suivi des plantages (une seule fois par lancement, les appels suivants ne refont rien), puis
     * envoie les rapports laissés par un plantage précédent. Renvoie le nombre de rapports envoyés.
     */
    suspend fun start(): AppResult<Int>

    /** Rapports reçus par le journal local (builds de debug), du plus récent au plus ancien. */
    fun observeJournal(): Flow<List<CrashReport>>
}
