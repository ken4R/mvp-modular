package com.mvp.feature.auth.data.source

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

// Framework Kotlin compilé en release pour une archive Xcode (scripts/build-ios-framework.sh release).
@OptIn(ExperimentalNativeApi::class)
internal actual fun isDemoAuthEnabled(): Boolean = Platform.isDebugBinary
