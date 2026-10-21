package com.mvp.core.notifications.di

import com.mvp.core.notifications.LocalNotificationScheduler
import com.mvp.core.notifications.UserNotificationsLocalNotificationScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

actual val notificationsModule: Module = module {
    single<LocalNotificationScheduler> { UserNotificationsLocalNotificationScheduler() }
}
