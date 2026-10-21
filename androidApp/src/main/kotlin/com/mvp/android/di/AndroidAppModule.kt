package com.mvp.android.di

import com.mvp.android.ui.auth.LoginViewModel
import com.mvp.android.ui.auth.SessionViewModel
import com.mvp.android.ui.catalog.CatalogViewModel
import com.mvp.android.ui.settings.SettingsViewModel
import com.mvp.android.ui.todo.TodoViewModel
import com.mvp.android.ui.weather.WeatherViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Dépendances propres à l'UI Android (ViewModels). */
val androidAppModule = module {
    viewModelOf(::TodoViewModel)
    viewModelOf(::WeatherViewModel)
    viewModelOf(::CatalogViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::SessionViewModel)
    viewModelOf(::LoginViewModel)
}
