package com.mvp.feature.catalog

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.getOrNull
import com.mvp.feature.catalog.domain.model.Catalog
import com.mvp.feature.catalog.domain.model.CatalogFilter
import com.mvp.feature.catalog.domain.model.CatalogSort
import com.mvp.feature.catalog.domain.model.Product
import com.mvp.feature.catalog.domain.model.ProductSummary
import com.mvp.feature.catalog.domain.repository.CatalogPage
import com.mvp.feature.catalog.domain.repository.CatalogRepository
import com.mvp.feature.catalog.domain.usecase.ObserveFilteredCatalogUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogFilterUseCaseTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val catalog = Catalog(
        products = listOf(
            product(1, "Mascara Essence", brand = "Essence", price = 9.99, rating = 4.9),
            product(2, "Crème hydratante", brand = null, price = 19.0, rating = 3.2),
            product(3, "iPhone 9", brand = "Apple", price = 549.0, rating = 4.4),
            product(4, "Parfum", brand = "Chanel", price = 89.0, rating = 4.4),
        ),
        total = 194,
        loadedCount = 4,
        updatedAt = 1_000L,
    )
    private val observe = ObserveFilteredCatalogUseCase(FixedCatalogRepository(catalog), dispatcher)

    private suspend fun ids(filter: CatalogFilter): List<Long> = observe(filter).first().getOrNull()!!.products.map { it.id }

    @Test
    fun noFilterKeepsServerOrderAndWholeCatalog() = scope.runTest {
        val result = observe(CatalogFilter()).first().getOrNull()!!

        assertEquals(listOf(1L, 2L, 3L, 4L), result.products.map { it.id })
        assertEquals(catalog, result.catalog)
        assertFalse(CatalogFilter(sort = CatalogSort.PriceAscending).narrowsResults)
    }

    @Test
    fun querySearchesTitleAndBrandWithoutCaseOrAccents() = scope.runTest {
        assertEquals(listOf(2L), ids(CatalogFilter(query = "creme")))
        assertEquals(listOf(3L), ids(CatalogFilter(query = "APPLE iphone")))
        assertEquals(listOf(4L), ids(CatalogFilter(query = "chanel")))
        assertTrue(CatalogFilter(query = "chanel").narrowsResults)
    }

    @Test
    fun topRatedKeepsRatingsFromFourStars() = scope.runTest {
        assertEquals(listOf(1L, 3L, 4L), ids(CatalogFilter(topRatedOnly = true)))
    }

    @Test
    fun sortsKeepServerOrderOnTies() = scope.runTest {
        assertEquals(listOf(1L, 2L, 4L, 3L), ids(CatalogFilter(sort = CatalogSort.PriceAscending)))
        assertEquals(listOf(3L, 4L, 2L, 1L), ids(CatalogFilter(sort = CatalogSort.PriceDescending)))
        assertEquals(listOf(1L, 3L, 4L, 2L), ids(CatalogFilter(sort = CatalogSort.BestRated)))
    }

    @Test
    fun filterAndSortCombine() = scope.runTest {
        assertEquals(listOf(3L, 4L, 1L), ids(CatalogFilter(topRatedOnly = true, sort = CatalogSort.PriceDescending)))
        assertEquals(listOf(4L), ids(CatalogFilter(query = "parfum", topRatedOnly = true, sort = CatalogSort.BestRated)))
    }

    private fun product(id: Long, title: String, brand: String?, price: Double, rating: Double) = ProductSummary(
        id = id,
        title = title,
        brand = brand,
        price = price,
        currencyCode = "USD",
        discountPercent = 0.0,
        rating = rating,
        thumbnailUrl = null,
    )
}

/** Cache figé : seule l'observation sert au filtrage. */
private class FixedCatalogRepository(private val catalog: Catalog) : CatalogRepository {
    override fun observeCatalog(): Flow<Catalog> = flowOf(catalog)
    override suspend fun cachedCatalog(): Catalog = catalog
    override suspend fun loadPage(page: CatalogPage, size: Int): AppResult<Unit> = error("non utilisé")
    override suspend fun cachedProduct(id: Long): Product? = null
    override suspend fun fetchProduct(id: Long): AppResult<Product> = error("non utilisé")
}
