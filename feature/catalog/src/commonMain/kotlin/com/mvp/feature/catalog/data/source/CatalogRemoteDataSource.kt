package com.mvp.feature.catalog.data.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** Accès distant au catalogue. Les exceptions Ktor remontent : le repository les convertit. */
internal interface CatalogRemoteDataSource {
    suspend fun getProducts(skip: Int, limit: Int): ProductsPageDto
    suspend fun getProduct(id: Long): ProductDto
}

/** DummyJSON : catalogue de démonstration gratuit, sans clé d'API, en HTTPS, paginé par `limit` / `skip`. */
internal class DummyJsonRemoteDataSource(private val client: HttpClient) : CatalogRemoteDataSource {

    override suspend fun getProducts(skip: Int, limit: Int): ProductsPageDto =
        client.get(PRODUCTS_URL) {
            parameter("limit", limit)
            parameter("skip", skip)
            // Sans les avis, dimensions, codes-barres... : une page pèse quelques ko au lieu de plusieurs dizaines.
            parameter("select", FIELDS)
        }.body()

    override suspend fun getProduct(id: Long): ProductDto =
        client.get("$PRODUCTS_URL/$id").body()

    companion object {
        const val PRODUCTS_URL = "https://dummyjson.com/products"
        private const val FIELDS = "title,description,category,brand,price,discountPercentage,rating,stock,thumbnail,images"
    }
}
