package com.mvp.core.crashreporting.data.sink

import com.mvp.core.crashreporting.domain.model.CrashReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Destination des rapports : l'outil de suivi des plantages. Brancher un vrai outil (Sentry, Crashlytics...) =
 * une nouvelle implémentation déclarée dans `crashReportingModule` (voir ADR 0020 pour ce qu'il faut régler).
 */
interface CrashReportSink {

    /** Envoie un rapport ; lève une exception en cas d'échec (le rapport reste alors en attente). */
    suspend fun send(report: CrashReport)

    /** Oublie ce que l'outil garde sur l'appareil : retrait du consentement et déconnexion. */
    suspend fun reset()
}

/**
 * Pas encore d'outil : les rapports « envoyés » restent en mémoire pour le journal de debug.
 * Rien ne quitte l'appareil.
 */
internal class DebugCrashReportSink(private val capacity: Int = DEFAULT_CAPACITY) : CrashReportSink {

    private val received = MutableStateFlow<List<CrashReport>>(emptyList())

    /** Derniers rapports reçus, du plus récent au plus ancien. */
    fun observeSent(): Flow<List<CrashReport>> = received.asStateFlow()

    override suspend fun send(report: CrashReport) = received.update { (listOf(report) + it).take(capacity) }

    override suspend fun reset() {
        received.value = emptyList()
    }

    companion object {
        const val DEFAULT_CAPACITY = 20
    }
}
