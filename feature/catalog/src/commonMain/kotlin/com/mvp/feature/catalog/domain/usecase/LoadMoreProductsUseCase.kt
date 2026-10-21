package com.mvp.feature.catalog.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.catalog.domain.repository.CatalogPage
import com.mvp.feature.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Charge la page suivante du catalogue (pagination infinie).
 * Règle métier : aucun appel réseau quand tous les produits sont déjà chargés.
 */
class LoadMoreProductsUseCase(
    private val repository: CatalogRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Unit>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Unit> =
        if (repository.cachedCatalog().hasMore) {
            repository.loadPage(CatalogPage.Next, PAGE_SIZE)
        } else {
            AppResult.Success(Unit)
        }

    companion object {
        /** Produits par page. */
        const val PAGE_SIZE = 20
    }
}
