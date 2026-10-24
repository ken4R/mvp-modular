package com.mvp.android.di

import com.mvp.android.ui.analytics.AnalyticsViewModel
import com.mvp.android.ui.appearance.AppearanceViewModel
import com.mvp.android.ui.applock.AppLockViewModel
import com.mvp.android.ui.auth.LoginViewModel
import com.mvp.android.ui.auth.SessionViewModel
import com.mvp.android.ui.catalog.CatalogViewModel
import com.mvp.android.ui.deeplink.DeepLinkViewModel
import com.mvp.android.ui.onboarding.OnboardingViewModel
import com.mvp.android.ui.settings.SettingsViewModel
import com.mvp.android.ui.todo.AttachmentReader
import com.mvp.android.ui.todo.TodoDetailViewModel
import com.mvp.android.ui.todo.TodoViewModel
import com.mvp.android.ui.weather.WeatherViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Dépendances propres à l'UI Android (ViewModels). */
val androidAppModule = module {
    viewModelOf(::TodoViewModel)
    // Fiche d'une tâche : note et pièces jointes (ADR 0014).
    single { AttachmentReader(androidContext()) }
    viewModelOf(::TodoDetailViewModel)
    viewModelOf(::WeatherViewModel)
    viewModelOf(::CatalogViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::SessionViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::AppLockViewModel)
    viewModelOf(::DeepLinkViewModel)
    viewModelOf(::AnalyticsViewModel)
    viewModelOf(::AppearanceViewModel)
    viewModelOf(::OnboardingViewModel)
}
