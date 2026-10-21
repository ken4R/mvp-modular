package com.mvp.core.location.di

import android.content.Context
import com.mvp.core.common.domain.location.LocationProvider
import com.mvp.core.location.AndroidLocationProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/** Le `Context` vient de `androidContext(...)`, passé à `initKoin` par l'application. */
actual val locationModule: Module = module {
    single<LocationProvider> { AndroidLocationProvider(get<Context>()) }
}
