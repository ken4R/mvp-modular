package com.mvp.feature.catalog

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.database.testing.createInMemoryDriver
import com.mvp.core.network.createHttpClient
import com.mvp.feature.catalog.data.db.CatalogDatabase
import com.mvp.feature.catalog.data.repository.CatalogRepositoryImpl
import com.mvp.feature.catalog.data.source.CatalogLocalDataSource
import com.mvp.feature.catalog.data.source.DummyJsonRemoteDataSource
import com.mvp.feature.catalog.domain.model.Catalog
import com.mvp.feature.catalog.domain.model.Product
import com.mvp.feature.catalog.domain.model.ProductSummary
import com.mvp.feature.catalog.domain.usecase.GetProductUseCase
import com.mvp.feature.catalog.domain.usecase.LoadMoreProductsUseCase
import com.mvp.feature.catalog.domain.usecase.ObserveCatalogUseCase
import com.mvp.feature.catalog.domain.usecase.RefreshCatalogUseCase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CatalogUseCasesTest {

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val driver = createInMemoryDriver(CatalogDatabase.Schema)
    private val requests = mutableListOf<HttpRequestData>()
    private var now = 1_000L

    /** Le serveur DummyJSON est simulé par un MockEngine qui répond avec [handler]. */
    private var handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondError(HttpStatusCode.InternalServerError) }

    private val client = createHttpClient(MockEngine { request -> requests += request; handler(this, request) })

    /** Une nouvelle instance sur la même base équivaut à un redémarrage de l'app. */
    private fun newRepository() = CatalogRepositoryImpl(
        DummyJsonRemoteDataSource(client),
        CatalogLocalDataSource(CatalogDatabase(driver), dispatcher),
        now = { now },
    )

    private val repository = newRepository()
    private val observeCatalog = ObserveCatalogUseCase(repository, dispatcher)
    private val refresh = RefreshCatalogUseCase(repository, dispatcher)
    private val loadMore = LoadMoreProductsUseCase(repository, dispatcher)
    private val getProduct = GetProductUseCase(repository, dispatcher)

    @AfterTest
    fun closeDriver() = driver.close()

    @Test
    fun catalogIsEmptyBeforeFirstLoad() = scope.runTest {
        val catalog = catalog()

        assertEquals(Catalog(emptyList(), total = 0, updatedAt = null), catalog)
        assertTrue(catalog.hasMore)
    }

    @Test
    fun refreshLoadsFirstPageIntoCache() = scope.runTest {
        handler = { respondJson(page(ids = 1..2, total = 3)) }

        assertEquals(AppResult.Success(Unit), refresh(NoParams))

        val catalog = catalog()
        assertEquals(listOf(summary(1), summary(2)), catalog.products)
        assertEquals(3, catalog.total)
        assertEquals(1_000L, catalog.updatedAt)
        assertTrue(catalog.hasMore)
        val url = requests.single().url
        assertEquals("dummyjson.com", url.host)
        assertEquals("/products", url.encodedPath)
        assertEquals("0", url.parameters["skip"])
        assertEquals("${LoadMoreProductsUseCase.PAGE_SIZE}", url.parameters["limit"])
    }

    @Test
    fun loadMoreAppendsNextPageAfterCachedProducts() = scope.runTest {
        handler = { respondJson(page(ids = 1..2, total = 3)) }
        refresh(NoParams)
        handler = { respondJson(page(ids = 3..3, total = 3)) }

        assertEquals(AppResult.Success(Unit), loadMore(NoParams))

        val catalog = catalog()
        assertEquals(listOf(1L, 2L, 3L), catalog.products.map { it.id })
        assertFalse(catalog.hasMore)
        assertEquals("2", requests.last().url.parameters["skip"])
    }

    @Test
    fun loadMoreDoesNotCallServerWhenEverythingIsLoaded() = scope.runTest {
        handler = { respondJson(page(ids = 1..2, total = 2)) }
        refresh(NoParams)
        requests.clear()

        assertEquals(AppResult.Success(Unit), loadMore(NoParams))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun emptyPageEndsPaginationEvenIfTotalSaysOtherwise() = scope.runTest {
        handler = { respondJson(page(ids = 1..2, total = 50)) }
        refresh(NoParams)
        handler = { respondJson(page(ids = IntRange.EMPTY, total = 50)) }

        loadMore(NoParams)

        assertFalse(catalog().hasMore)
    }

    @Test
    fun refreshReplacesTheWholeCache() = scope.runTest {
        handler = { respondJson(page(ids = 1..2, total = 4)) }
        refresh(NoParams)
        handler = { respondJson(page(ids = 3..4, total = 4)) }
        loadMore(NoParams)
        now = 2_000L
        handler = { respondJson(page(ids = 10..11, total = 2)) }

        refresh(NoParams)

        val catalog = catalog()
        assertEquals(listOf(10L, 11L), catalog.products.map { it.id })
        assertEquals(2_000L, catalog.updatedAt)
    }

    @Test
    fun offlineRefreshKeepsCacheAcrossRestart() = scope.runTest {
        handler = { respondJson(page(ids = 1..2, total = 2)) }
        refresh(NoParams)
        handler = { throw ConnectionLostException() }

        // Redémarrage de l'app, sans réseau.
        val restarted = newRepository()
        val result = RefreshCatalogUseCase(restarted, dispatcher)(NoParams)

        assertEquals(AppResult.Failure(AppError.Network), result)
        val catalog = ObserveCatalogUseCase(restarted, dispatcher)(NoParams).first()
        assertEquals(listOf(1L, 2L), (catalog as AppResult.Success).data.products.map { it.id })
    }

    @Test
    fun malformedResponseIsReportedAndCacheIsKept() = scope.runTest {
        handler = { respondJson(page(ids = 1..1, total = 1)) }
        refresh(NoParams)
        handler = { respondJson("""{"products": [{"id": 5}]}""") }

        assertIs<AppError.Unknown>((refresh(NoParams) as AppResult.Failure).error)
        assertEquals(listOf(1L), catalog().products.map { it.id })
    }

    @Test
    fun productDetailComesFromCacheWithoutNetwork() = scope.runTest {
        handler = { respondJson(page(ids = 1..2, total = 2)) }
        refresh(NoParams)
        requests.clear()
        handler = { throw ConnectionLostException() }

        assertEquals(AppResult.Success(product(2)), getProduct(2L))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun productMissingFromCacheIsFetchedFromServer() = scope.runTest {
        handler = { respondJson(productJson(42)) }

        assertEquals(AppResult.Success(product(42)), getProduct(42L))
        assertEquals("/products/42", requests.single().url.encodedPath)
        // Un produit hors des pages chargées n'est pas ajouté à la liste.
        assertTrue(catalog().products.isEmpty())
    }

    @Test
    fun unknownProductIsReportedAsNotFound() = scope.runTest {
        handler = { respondError(HttpStatusCode.NotFound) }

        assertEquals(AppResult.Failure(AppError.NotFound), getProduct(999L))
    }

    @Test
    fun nonHttpsImagesAndBlankBrandAreDropped() = scope.runTest {
        handler = {
            respondJson(
                """
                {"products": [{
                  "id": 7, "title": "Lampe", "price": 12.5, "brand": " ",
                  "thumbnail": "http://cdn.example.com/t.png",
                  "images": ["https://cdn.example.com/1.png", "file:///etc/passwd", "http://cdn.example.com/2.png"]
                }], "total": 1}
                """.trimIndent(),
            )
        }
        refresh(NoParams)

        val product = (getProduct(7L) as AppResult.Success).data
        assertEquals(null, product.brand)
        assertEquals(null, product.thumbnailUrl)
        assertEquals(listOf("https://cdn.example.com/1.png"), product.imageUrls)
    }

    private suspend fun catalog(): Catalog = (observeCatalog(NoParams).first() as AppResult.Success).data

    private fun MockRequestHandleScope.respondJson(json: String) =
        respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private class ConnectionLostException : Exception("connexion perdue")

    private companion object {
        fun product(id: Int) = Product(
            id = id.toLong(),
            title = "Produit $id",
            description = "Description $id",
            category = "beauty",
            brand = "Marque",
            price = 9.99,
            currencyCode = "USD",
            discountPercent = 10.5,
            rating = 4.2,
            stock = 30,
            thumbnailUrl = "https://cdn.dummyjson.com/$id/thumbnail.webp",
            imageUrls = listOf("https://cdn.dummyjson.com/$id/1.webp", "https://cdn.dummyjson.com/$id/2.webp"),
        )

        fun summary(id: Int) = with(product(id)) {
            ProductSummary(this.id, title, brand, price, currencyCode, discountPercent, rating, thumbnailUrl)
        }

        fun productJson(id: Int) = """
            {
              "id": $id, "title": "Produit $id", "description": "Description $id", "category": "beauty",
              "brand": "Marque", "price": 9.99, "discountPercentage": 10.5, "rating": 4.2, "stock": 30,
              "tags": ["mascara"], "reviews": [],
              "thumbnail": "https://cdn.dummyjson.com/$id/thumbnail.webp",
              "images": ["https://cdn.dummyjson.com/$id/1.webp", "https://cdn.dummyjson.com/$id/2.webp"]
            }
        """.trimIndent()

        fun page(ids: IntRange, total: Int) =
            """{"products": [${ids.joinToString(",") { productJson(it) }}], "total": $total, "skip": 0, "limit": 20}"""
    }
}
