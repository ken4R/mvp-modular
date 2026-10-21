package com.mvp.core.notifications.di

import org.koin.core.module.Module

/** Fournit le [com.mvp.core.notifications.LocalNotificationScheduler] de la plateforme. */
expect val notificationsModule: Module
