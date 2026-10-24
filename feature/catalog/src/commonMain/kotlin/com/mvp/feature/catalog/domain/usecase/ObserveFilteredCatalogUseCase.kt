package com.mvp.feature.catalog.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.feature.catalog.domain.model.CatalogFilter
import com.mvp.feature.catalog.domain.model.FilteredCatalog
import com.mvp.feature.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Catalogue en cache filtré et trié. La recherche porte sur les pages déjà chargées : en arrivant en bas de la
 * liste, la page suivante est chargée comme sans filtre, et filtrée à son tour.
 */
class ObserveFilteredCatalogUseCase(
    private val repository: CatalogRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<CatalogFilter, FilteredCatalog>(dispatcher) {

    override fun execute(params: CatalogFilter): Flow<AppResult<FilteredCatalog>> =
        repository.observeCatalog().map { catalog -> AppResult.Success(FilteredCatalog(params.applyTo(catalog.products), catalog)) }
}
