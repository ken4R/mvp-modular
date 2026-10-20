package com.mvp.core.database.di

import com.mvp.core.database.DatabaseDriverFactory
import com.mvp.core.database.NativeDatabaseDriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

actual val databaseModule: Module = module {
    single<DatabaseDriverFactory> { NativeDatabaseDriverFactory() }
}
