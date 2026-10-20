package com.mvp.core.database.di

import android.content.Context
import com.mvp.core.database.AndroidDatabaseDriverFactory
import com.mvp.core.database.DatabaseDriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

/** Le `Context` vient de `androidContext(...)`, passé à `initKoin` par l'application. */
actual val databaseModule: Module = module {
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(get<Context>()) }
}
