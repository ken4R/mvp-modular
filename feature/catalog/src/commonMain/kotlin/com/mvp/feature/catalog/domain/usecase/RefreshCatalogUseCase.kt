package com.mvp.feature.catalog.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.catalog.domain.repository.CatalogPage
import com.mvp.feature.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Recharge la première page du catalogue et remplace le cache.
 * En cas d'échec (hors ligne...), le cache reste intact : l'écran continue d'afficher la dernière liste chargée.
 */
class RefreshCatalogUseCase(
    private val repository: CatalogRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Unit>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Unit> =
        repository.loadPage(CatalogPage.First, LoadMoreProductsUseCase.PAGE_SIZE)
}
