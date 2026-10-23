package com.mvp.feature.auth.data.source

import com.mvp.feature.auth.BuildConfig

// Constante par type de build (build.gradle.kts) : en release, R8 supprime le faux service de l'APK.
internal actual fun isDemoAuthEnabled(): Boolean = BuildConfig.DEMO_AUTH
