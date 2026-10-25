package com.mvp.core.featureflags.data.platform

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

// Framework Kotlin compilé en release pour une archive Xcode (scripts/build-ios-framework.sh release).
@OptIn(ExperimentalNativeApi::class)
internal actual fun isDevBuild(): Boolean = Platform.isDebugBinary
