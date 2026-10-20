package com.mvp.core.database.di

import org.koin.core.module.Module

/** Fournit la [com.mvp.core.database.DatabaseDriverFactory] de la plateforme. */
expect val databaseModule: Module
