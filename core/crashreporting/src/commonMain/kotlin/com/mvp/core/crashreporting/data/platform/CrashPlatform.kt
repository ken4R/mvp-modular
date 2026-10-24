package com.mvp.core.crashreporting.data.platform

/**
 * Branche [onCrash] sur les plantages Kotlin non rattrapés (et Java sur Android), en gardant le comportement
 * d'origine : [onCrash] est appelé sur le fil qui plante, puis l'app s'arrête comme avant.
 */
internal expect fun installCrashHandler(onCrash: (Throwable) -> Unit)

/** Lignes de pile de cette exception seule (sans le message ni les causes). */
internal expect fun Throwable.stackFrames(): List<String>

/** Système et sa version, sans modèle d'appareil ni identifiant. */
internal expect fun platformDescription(): String
