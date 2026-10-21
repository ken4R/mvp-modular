package com.mvp.feature.catalog.domain.repository

import com.mvp.core.common.domain.result.AppResult
import com.mvp.feature.catalog.domain.model.Catalog
import com.mvp.feature.catalog.domain.model.Product
import kotlinx.coroutines.flow.Flow

/** Page à charger depuis le serveur. */
enum class CatalogPage {
    /** Première page : remplace tout le cache (actualisation). */
    First,

    /** Page qui suit les produits déjà en cache. */
    Next,
}

interface CatalogRepository {
    /** Catalogue en cache, mis à jour à chaque page chargée. */
    fun observeCatalog(): Flow<Catalog>

    /** Catalogue en cache à cet instant. */
    suspend fun cachedCatalog(): Catalog

    /** Charge une page de [size] produits depuis le serveur et l'enregistre dans le cache. */
    suspend fun loadPage(page: CatalogPage, size: Int): AppResult<Unit>

    /** Produit en cache, ou `null` s'il n'y est pas. */
    suspend fun cachedProduct(id: Long): Product?

    /** Produit lu sur le serveur. */
    suspend fun fetchProduct(id: Long): AppResult<Product>
}
