package com.mvp.core.location.di

import com.mvp.core.common.domain.location.LocationProvider
import com.mvp.core.location.CoreLocationProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val locationModule: Module = module {
    single<LocationProvider> { CoreLocationProvider() }
}
