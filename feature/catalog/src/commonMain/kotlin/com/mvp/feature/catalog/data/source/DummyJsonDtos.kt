package com.mvp.feature.catalog.data.source

import kotlinx.serialization.Serializable

// Réponses de l'API DummyJSON, limitées aux champs utilisés.

@Serializable
internal data class ProductsPageDto(
    val products: List<ProductDto> = emptyList(),
    val total: Int,
)

/** Seuls `id`, `title` et `price` sont toujours présents ; `brand` manque pour certains produits. */
@Serializable
internal data class ProductDto(
    val id: Long,
    val title: String,
    val price: Double,
    val description: String = "",
    val category: String = "",
    val brand: String? = null,
    val discountPercentage: Double = 0.0,
    val rating: Double = 0.0,
    val stock: Int = 0,
    val thumbnail: String? = null,
    val images: List<String> = emptyList(),
)
