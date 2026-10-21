package com.mvp.feature.catalog.domain.model

/** Produit tel qu'affiché dans la liste du catalogue. */
data class ProductSummary(
    val id: Long,
    val title: String,
    val brand: String?,
    val price: Double,
    /** Code ISO 4217 de la devise de [price] (ex. « USD »). */
    val currencyCode: String,
    /** Remise en pourcentage, 0 sans remise. */
    val discountPercent: Double,
    /** Note moyenne sur 5. */
    val rating: Double,
    /** Vignette en HTTPS, ou `null` si le serveur n'en donne pas. */
    val thumbnailUrl: String?,
)

/** Fiche détaillée d'un produit. */
data class Product(
    val id: Long,
    val title: String,
    val description: String,
    val category: String,
    val brand: String?,
    val price: Double,
    val currencyCode: String,
    val discountPercent: Double,
    val rating: Double,
    /** Quantité en stock. */
    val stock: Int,
    val thumbnailUrl: String?,
    /** Images en HTTPS, dans l'ordre du serveur ; peut être vide. */
    val imageUrls: List<String>,
)
