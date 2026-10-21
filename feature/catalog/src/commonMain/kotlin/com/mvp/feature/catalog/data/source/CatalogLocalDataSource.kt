package com.mvp.feature.catalog.data.source

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.mvp.feature.catalog.data.db.CatalogDatabase
import com.mvp.feature.catalog.domain.model.Catalog
import com.mvp.feature.catalog.domain.model.Product
import com.mvp.feature.catalog.domain.model.ProductSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Les prix de DummyJSON sont en dollars US. */
internal const val CATALOG_CURRENCY = "USD"

/** Cache SQLite du catalogue : les pages déjà vues restent consultables hors ligne, même après un redémarrage. */
internal class CatalogLocalDataSource(
    database: CatalogDatabase,
    private val dispatcher: CoroutineDispatcher,
) {
    private val queries = database.catalogCacheQueries

    fun observe(): Flow<Catalog> = combine(
        queries.selectSummaries(::toSummary).asFlow().mapToList(dispatcher),
        queries.selectInfo { total, updatedAt -> total to updatedAt }.asFlow().mapToOneOrNull(dispatcher),
    ) { products, info ->
        Catalog(products = products, total = info?.first?.toInt() ?: 0, updatedAt = info?.second)
    }.distinctUntilChanged()

    fun count(): Int = queries.countProducts().executeAsOne().toInt()

    /** Remplace tout le cache par la première page (actualisation). */
    fun replaceAll(products: List<Product>, total: Int, updatedAt: Long) = queries.transaction {
        queries.deleteAllImages()
        queries.deleteAllProducts()
        insertAll(products, startPosition = 0)
        queries.upsertInfo(total.toLong(), updatedAt)
    }

    /** Ajoute une page à la suite des produits déjà en cache. */
    fun append(products: List<Product>, startPosition: Int, total: Int, updatedAt: Long) = queries.transaction {
        insertAll(products, startPosition)
        queries.upsertInfo(total.toLong(), updatedAt)
    }

    fun product(id: Long): Product? = queries.transactionWithResult {
        queries.selectProduct(id, ::toProduct).executeAsOneOrNull()
            ?.copy(imageUrls = queries.selectImages(id).executeAsList())
    }

    /** Met à jour un produit déjà en cache, à la même place ; ne fait rien s'il n'y est pas. */
    fun updateIfCached(product: Product) = queries.transaction {
        val position = queries.selectPosition(product.id).executeAsOneOrNull() ?: return@transaction
        insert(product, position)
    }

    private fun insertAll(products: List<Product>, startPosition: Int) =
        products.forEachIndexed { index, product -> insert(product, (startPosition + index).toLong()) }

    private fun insert(product: Product, position: Long) = with(product) {
        queries.upsertProduct(
            id, position, title, description, category, brand, price, discountPercent, rating, stock.toLong(), thumbnailUrl,
        )
        queries.deleteImages(id)
        imageUrls.forEachIndexed { index, url -> queries.insertImage(id, index.toLong(), url) }
    }
}

private fun toSummary(
    id: Long,
    title: String,
    brand: String?,
    price: Double,
    discountPercent: Double,
    rating: Double,
    thumbnailUrl: String?,
) = ProductSummary(id, title, brand, price, CATALOG_CURRENCY, discountPercent, rating, thumbnailUrl)

@Suppress("UNUSED_PARAMETER")
private fun toProduct(
    id: Long,
    position: Long,
    title: String,
    description: String,
    category: String,
    brand: String?,
    price: Double,
    discountPercent: Double,
    rating: Double,
    stock: Long,
    thumbnailUrl: String?,
) = Product(
    id = id,
    title = title,
    description = description,
    category = category,
    brand = brand,
    price = price,
    currencyCode = CATALOG_CURRENCY,
    discountPercent = discountPercent,
    rating = rating,
    stock = stock.toInt(),
    thumbnailUrl = thumbnailUrl,
    imageUrls = emptyList(),
)
