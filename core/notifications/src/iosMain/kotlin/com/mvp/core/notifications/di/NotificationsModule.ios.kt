package com.mvp.core.notifications.di

import com.mvp.core.notifications.LocalNotificationScheduler
import com.mvp.core.notifications.LocationNotificationScheduler
import com.mvp.core.notifications.UserNotificationsLocalNotificationScheduler
import com.mvp.core.notifications.UserNotificationsLocationNotificationScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

actual val notificationsModule: Module = module {
    single<LocalNotificationScheduler> { UserNotificationsLocalNotificationScheduler() }
    // Rappels par lieu (ADR 0023) : notifications déclenchées par iOS à l'arrivée dans une zone.
    single<LocationNotificationScheduler> { UserNotificationsLocationNotificationScheduler() }
}
