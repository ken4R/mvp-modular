package com.mvp.android.di

import com.mvp.android.ui.todo.TodoViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Dépendances propres à l'UI Android (ViewModels). */
val androidAppModule = module {
    viewModelOf(::TodoViewModel)
}
