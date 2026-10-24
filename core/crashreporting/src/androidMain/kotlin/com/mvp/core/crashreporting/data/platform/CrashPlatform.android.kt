package com.mvp.core.crashreporting.data.platform

import android.os.Build
import kotlin.system.exitProcess

internal actual fun installCrashHandler(onCrash: (Throwable) -> Unit) {
    // Celui d'Android (affiche « L'app s'est arrêtée » et tue le processus) : appelé ensuite, comme avant.
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            onCrash(throwable)
        } catch (_: Throwable) {
            // Le suivi des plantages ne doit jamais empêcher l'arrêt normal de l'app.
        }
        if (previous != null) {
            previous.uncaughtException(thread, throwable)
        } else {
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }
    }
}

// « com.mvp.feature.todo.data.TodoRepositoryImpl.add(TodoRepositoryImpl.kt:42) » : classe, méthode, fichier, ligne.
internal actual fun Throwable.stackFrames(): List<String> = stackTrace.map { it.toString() }

internal actual fun platformDescription(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
