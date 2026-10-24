package com.mvp.core.crashreporting.data

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.crash.CrashReportingConsent
import com.mvp.core.common.domain.crash.CrashReportingConsentObserver
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.crashreporting.data.sink.CrashReportSink
import com.mvp.core.crashreporting.data.store.PendingCrashStore
import com.mvp.core.crashreporting.domain.model.CrashReport
import com.mvp.core.crashreporting.domain.model.CrashScrubber
import com.mvp.core.crashreporting.domain.model.CrashedException
import com.mvp.core.crashreporting.domain.repository.CrashReportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * Suivi des plantages (ADR 0020). Au plantage, il n'y a plus le temps de lire la base : le consentement est
 * suivi en mémoire ([enabled], tenu à jour par [CrashReportingConsent]) et le rapport, nettoyé, est écrit dans un
 * fichier. Il part au lancement suivant, après une nouvelle lecture du consentement.
 */
internal class CrashReporter(
    private val consent: CrashReportingConsent,
    private val store: PendingCrashStore,
    private val sink: CrashReportSink,
    private val journal: Flow<List<CrashReport>>,
    private val now: () -> Long,
    private val newId: () -> String,
    private val platform: () -> String,
    private val installHandler: ((Throwable) -> Unit) -> Unit,
    private val stackFrames: (Throwable) -> List<String>,
    private val scope: CoroutineScope,
) : CrashReportRepository, CrashReportingConsentObserver {

    /** Lu par le gestionnaire de plantage : `false` tant que le consentement n'est pas lu (début du lancement). */
    @Volatile
    private var enabled = false

    @Volatile
    private var platformName = ""

    private val startMutex = Mutex()
    private val sendMutex = Mutex()
    private var started = false

    override suspend fun start(): AppResult<Int> {
        startMutex.withLock {
            if (!started) {
                started = true
                platformName = platform()
                installHandler(::record)
                scope.launch {
                    consent.observeGranted().collect { granted ->
                        enabled = granted
                        // Consentement retiré depuis un autre écran, perdu à la déconnexion ou texte changé.
                        if (!granted) sendMutex.withLock { runCatching { store.deleteAll() } }
                    }
                }
            }
        }
        return sendPending()
    }

    override fun observeJournal(): Flow<List<CrashReport>> = journal

    /** Rapports d'un plantage précédent : envoyés avec le consentement, effacés sans. */
    internal suspend fun sendPending(): AppResult<Int> = sendMutex.withLock {
        safeCall {
            if (!consent.observeGranted().first()) {
                store.deleteAll()
                0
            } else {
                // Un envoi raté s'arrête là : les rapports restants repartiront au lancement suivant.
                store.readAll().onEach { report ->
                    sink.send(report)
                    store.delete(report.id)
                }.size
            }
        }
    }

    /** Appelé sur le fil qui plante : ne lit rien sur le disque, n'attend rien, ne lève jamais d'exception. */
    internal fun record(throwable: Throwable) {
        if (!enabled) return
        try {
            val chain = generateSequence(throwable) { it.cause }.take(CrashScrubber.MAX_EXCEPTIONS).toList()
            val exceptions = chain.map { exception ->
                // Jamais `message` : il peut citer ce que l'utilisateur a saisi.
                CrashedException(type = exception::class.qualifiedName ?: "Throwable", frames = stackFrames(exception))
            }
            store.save(
                CrashReport(
                    id = newId(),
                    occurredAt = now(),
                    platform = platformName,
                    exceptions = CrashScrubber.scrub(exceptions),
                ),
            )
        } catch (_: Throwable) {
            // Rapport perdu : l'app s'arrête de toute façon.
        }
    }

    override suspend fun onCrashReportingConsentRevoked() {
        enabled = false
        sendMutex.withLock {
            store.deleteAll()
            sink.reset()
        }
    }

    /** Déconnexion : plus aucun rapport de cette session sur l'appareil ni dans l'outil. */
    suspend fun clear() = onCrashReportingConsentRevoked()
}
