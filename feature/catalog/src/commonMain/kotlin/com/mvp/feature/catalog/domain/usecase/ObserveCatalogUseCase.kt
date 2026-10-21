package com.mvp.feature.catalog.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.catalog.domain.model.Catalog
import com.mvp.feature.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Catalogue en cache : disponible hors ligne, mis à jour à chaque page chargée. */
class ObserveCatalogUseCase(
    private val repository: CatalogRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, Catalog>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<Catalog>> =
        repository.observeCatalog().map { AppResult.Success(it) }
}
