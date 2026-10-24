package com.mvp.feature.catalog.di

import com.mvp.core.common.dispatcher.AppDispatchers
import com.mvp.core.database.DatabaseDriverFactory
import com.mvp.feature.catalog.data.db.CatalogDatabase
import com.mvp.feature.catalog.data.repository.CatalogRepositoryImpl
import com.mvp.feature.catalog.data.source.CatalogLocalDataSource
import com.mvp.feature.catalog.data.source.CatalogRemoteDataSource
import com.mvp.feature.catalog.data.source.DummyJsonRemoteDataSource
import com.mvp.feature.catalog.domain.repository.CatalogRepository
import com.mvp.feature.catalog.domain.usecase.GetProductUseCase
import com.mvp.feature.catalog.domain.usecase.LoadMoreProductsUseCase
import com.mvp.feature.catalog.domain.usecase.ObserveCatalogUseCase
import com.mvp.feature.catalog.domain.usecase.ObserveFilteredCatalogUseCase
import com.mvp.feature.catalog.domain.usecase.RefreshCatalogUseCase
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private val systemNow: () -> Long = { Clock.System.now().toEpochMilliseconds() }

/**
 * Nécessite le `HttpClient` de `networkModule` (core:network) et la `DatabaseDriverFactory` de `databaseModule`
 * (core:database). Le catalogue est public, sans lien avec le compte : pas de `UserSessionObserver`, le cache
 * survit à la déconnexion.
 */
val catalogModule = module {
    // Data
    single { CatalogDatabase(get<DatabaseDriverFactory>().create(CatalogDatabase.Schema, "catalog.db")) }
    single<CatalogRemoteDataSource> { DummyJsonRemoteDataSource(get()) }
    single { CatalogLocalDataSource(get(), get<AppDispatchers>().io) }
    single<CatalogRepository> { CatalogRepositoryImpl(get(), get(), systemNow) }

    // Domain
    factory { ObserveCatalogUseCase(get(), get<AppDispatchers>().io) }
    factory { ObserveFilteredCatalogUseCase(get(), get<AppDispatchers>().io) }
    factory { RefreshCatalogUseCase(get(), get<AppDispatchers>().io) }
    factory { LoadMoreProductsUseCase(get(), get<AppDispatchers>().io) }
    factory { GetProductUseCase(get(), get<AppDispatchers>().io) }
}
