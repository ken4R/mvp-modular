package com.mvp.feature.catalog.data.repository

import com.mvp.core.common.data.safeCall
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.onSuccess
import com.mvp.core.network.safeApiCall
import com.mvp.feature.catalog.data.source.CATALOG_CURRENCY
import com.mvp.feature.catalog.data.source.CatalogLocalDataSource
import com.mvp.feature.catalog.data.source.CatalogRemoteDataSource
import com.mvp.feature.catalog.data.source.ProductDto
import com.mvp.feature.catalog.domain.model.Catalog
import com.mvp.feature.catalog.domain.model.Product
import com.mvp.feature.catalog.domain.repository.CatalogPage
import com.mvp.feature.catalog.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class CatalogRepositoryImpl(
    private val remote: CatalogRemoteDataSource,
    private val local: CatalogLocalDataSource,
    private val now: () -> Long,
) : CatalogRepository {

    /** Une page à la fois : la position de la page suivante dépend de ce qui est déjà en cache. */
    private val loading = Mutex()

    override fun observeCatalog(): Flow<Catalog> = local.observe()

    override suspend fun cachedCatalog(): Catalog = local.observe().first()

    override suspend fun loadPage(page: CatalogPage, size: Int): AppResult<Unit> = loading.withLock {
        val skip = when (page) {
            CatalogPage.First -> 0
            CatalogPage.Next -> local.count()
        }
        when (val response = safeApiCall { remote.getProducts(skip, size) }) {
            is AppResult.Failure -> response
            is AppResult.Success -> safeCall {
                val products = response.data.products.map { it.toDomain() }
                // Page vide : fin du catalogue, même si le total annoncé est plus grand (on ne boucle pas).
                val total = if (products.isEmpty()) skip else maxOf(response.data.total, skip + products.size)
                when (page) {
                    CatalogPage.First -> local.replaceAll(products, total, now())
                    CatalogPage.Next -> local.append(products, skip, total, now())
                }
            }
        }
    }

    override suspend fun cachedProduct(id: Long): Product? = local.product(id)

    override suspend fun fetchProduct(id: Long): AppResult<Product> =
        safeApiCall { remote.getProduct(id).toDomain() }.onSuccess { product ->
            // Le cache n'est qu'un plus : un échec d'écriture ne masque pas le produit reçu.
            safeCall { local.updateIfCached(product) }
        }
}

internal fun ProductDto.toDomain() = Product(
    id = id,
    title = title,
    description = description,
    category = category,
    brand = brand?.takeIf { it.isNotBlank() },
    price = price,
    currencyCode = CATALOG_CURRENCY,
    discountPercent = discountPercentage,
    rating = rating,
    stock = stock,
    thumbnailUrl = thumbnail?.secureUrlOrNull(),
    imageUrls = images.mapNotNull { it.secureUrlOrNull() },
)

/** Seules les images en HTTPS sont gardées : une URL en clair ou d'un autre schéma (file:, data:...) est ignorée. */
private fun String.secureUrlOrNull(): String? = trim().takeIf { it.startsWith("https://", ignoreCase = true) }
