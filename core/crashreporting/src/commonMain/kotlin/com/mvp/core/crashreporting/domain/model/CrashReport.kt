package com.mvp.core.crashreporting.domain.model

/**
 * Rapport d'un plantage, tel qu'il quitte l'appareil. Jamais le message d'une exception (il peut citer un titre
 * de tâche, une note, une ville), seulement le type de chaque exception et l'endroit du code où elle est partie.
 */
data class CrashReport(
    /** Identifiant aléatoire du rapport, sans lien avec le compte ni l'appareil. */
    val id: String,
    /** Heure du plantage (epoch ms). */
    val occurredAt: Long,
    /** Système et sa version (« Android 15 », « iOS Version 18.1 (Build 22B83) »). */
    val platform: String,
    /** L'exception levée puis ses causes, dans l'ordre. */
    val exceptions: List<CrashedException>,
)

/** Une exception de la chaîne : son type (nom de classe) et ses lignes de pile. */
data class CrashedException(val type: String, val frames: List<String>)

/** Levée par le bouton « Provoquer un plantage » des builds de debug, pour vérifier le suivi de bout en bout. */
class TestCrashException : IllegalStateException("Plantage de test (debug)")
