package com.mvp.core.featureflags.data.platform

import com.mvp.core.featureflags.BuildConfig

internal actual fun isDevBuild(): Boolean = BuildConfig.DEV_FLAGS
