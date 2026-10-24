package com.mvp.core.crashreporting.data.platform

import platform.Foundation.NSProcessInfo
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException

/**
 * Exceptions Kotlin non rattrapées seulement (y compris celles qui remontent vers Swift sans `@Throws`) : un
 * plantage Swift ou Objective-C n'est pas vu, il faudrait un vrai outil (ADR 0020).
 */
@OptIn(ExperimentalNativeApi::class)
internal actual fun installCrashHandler(onCrash: (Throwable) -> Unit) {
    var previous: ((Throwable) -> Unit)? = null
    previous = setUnhandledExceptionHook { throwable ->
        try {
            onCrash(throwable)
        } catch (_: Throwable) {
            // Le suivi des plantages ne doit jamais empêcher l'arrêt normal de l'app.
        }
        // Avec un hook installé, une coroutine qui échoue ne termine plus l'app d'elle-même : on la termine
        // comme le ferait le runtime sans hook (message dans la console, puis arrêt).
        val chained = previous
        if (chained != null) chained(throwable)
        terminateWithUnhandledException(throwable)
    }
}

// « 12  Shared  0x0000000104a2b3c4  kfun:com.mvp.feature.todo…#add(…){} + 52 (…/TodoRepositoryImpl.kt:42:9) ».
@OptIn(ExperimentalNativeApi::class)
internal actual fun Throwable.stackFrames(): List<String> = getStackTrace().toList()

// « Version 18.1 (Build 22B83) » : lisible depuis n'importe quel fil, contrairement à UIDevice.
internal actual fun platformDescription(): String = "iOS ${NSProcessInfo.processInfo.operatingSystemVersionString}"
