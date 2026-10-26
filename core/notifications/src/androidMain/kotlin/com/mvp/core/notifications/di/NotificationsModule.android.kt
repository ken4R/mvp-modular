package com.mvp.core.notifications.di

import android.content.Context
import com.mvp.core.notifications.LocalNotificationScheduler
import com.mvp.core.notifications.LocationNotificationScheduler
import com.mvp.core.notifications.ProximityLocationNotificationScheduler
import com.mvp.core.notifications.WorkManagerLocalNotificationScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

/** Le `Context` vient de `androidContext(...)`, passé à `initKoin` par l'application. */
actual val notificationsModule: Module = module {
    single<LocalNotificationScheduler> { WorkManagerLocalNotificationScheduler(get<Context>()) }
    // Rappels par lieu (ADR 0023) : alertes de proximité du système, sans Google Play Services.
    single<LocationNotificationScheduler> { ProximityLocationNotificationScheduler(get<Context>()) }
}
