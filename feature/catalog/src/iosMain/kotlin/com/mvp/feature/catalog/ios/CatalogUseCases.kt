package com.mvp.feature.catalog.ios

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.ios.Subscription
import com.mvp.core.common.ios.SwiftUseCases
import com.mvp.feature.catalog.domain.model.Catalog
import com.mvp.feature.catalog.domain.model.CatalogFilter
import com.mvp.feature.catalog.domain.model.FilteredCatalog
import com.mvp.feature.catalog.domain.usecase.GetProductUseCase
import com.mvp.feature.catalog.domain.usecase.LoadMoreProductsUseCase
import com.mvp.feature.catalog.domain.usecase.ObserveCatalogUseCase
import com.mvp.feature.catalog.domain.usecase.ObserveFilteredCatalogUseCase
import com.mvp.feature.catalog.domain.usecase.RefreshCatalogUseCase
import org.koin.core.component.get

/**
 * Point d'accès Swift aux use cases du catalogue.
 * Koin n'est pas utilisable directement depuis Swift. Les use cases suspend s'appellent en `async` côté Swift ;
 * les Flow passent par des callbacks (voir SwiftUseCases).
 */
object CatalogUseCases : SwiftUseCases() {
    fun refreshCatalog(): RefreshCatalogUseCase = get()
    fun loadMoreProducts(): LoadMoreProductsUseCase = get()
    fun getProduct(): GetProductUseCase = get()

    /** Catalogue en cache (disponible hors ligne). Les callbacks sont appelés sur le main thread. */
    fun observeCatalog(onCatalog: (Catalog) -> Unit, onError: (AppError) -> Unit): Subscription =
        get<ObserveCatalogUseCase>()(NoParams).subscribe(onCatalog, onError)

    /**
     * Catalogue en cache filtré et trié selon [filter] (recherche sur les pages déjà chargées). Un nouveau filtre
     * demande un nouvel abonnement, l'ancien étant annulé. Les callbacks sont appelés sur le main thread.
     */
    fun observeFilteredCatalog(
        filter: CatalogFilter,
        onCatalog: (FilteredCatalog) -> Unit,
        onError: (AppError) -> Unit,
    ): Subscription =
        get<ObserveFilteredCatalogUseCase>()(filter).subscribe(onCatalog, onError)
}
