package com.mvp.feature.catalog.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.catalog.domain.model.Product
import com.mvp.feature.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Fiche d'un produit à partir de son identifiant.
 * Règle métier : le cache d'abord (le détail s'ouvre aussi hors ligne), le serveur seulement si le produit
 * n'y est pas (par exemple ouvert depuis un lien).
 */
class GetProductUseCase(
    private val repository: CatalogRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<Long, Product>(dispatcher) {

    override suspend fun execute(params: Long): AppResult<Product> =
        repository.cachedProduct(params)?.let { AppResult.Success(it) } ?: repository.fetchProduct(params)
}
