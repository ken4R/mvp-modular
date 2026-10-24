package com.mvp.feature.catalog.domain.model

import com.mvp.core.common.domain.search.SearchText

/** Ordre de la liste du catalogue. */
enum class CatalogSort {
    /** Ordre du serveur. */
    ServerOrder,
    PriceAscending,
    PriceDescending,

    /** Mieux notés d'abord. */
    BestRated,
}

/**
 * Recherche et filtres du catalogue, faits sur les produits déjà chargés (donc aussi hors ligne). [query] : mots
 * cherchés dans le nom et la marque, sans casse ni accents. [topRatedOnly] : note d'au moins [TOP_RATED_MIN].
 */
data class CatalogFilter(
    val query: String = "",
    val topRatedOnly: Boolean = false,
    val sort: CatalogSort = CatalogSort.ServerOrder,
) {
    /** `true` si la recherche ou le filtre retire des produits (le tri seul n'en retire aucun). */
    val narrowsResults: Boolean get() = SearchText.terms(query).isNotEmpty() || topRatedOnly

    fun applyTo(products: List<ProductSummary>): List<ProductSummary> {
        val terms = SearchText.terms(query)
        val matching = products.filter { product ->
            (!topRatedOnly || product.rating >= TOP_RATED_MIN) && SearchText.matches(terms, product.title, product.brand)
        }
        // Tris stables : à égalité, l'ordre du serveur est gardé. Le prix est comparé tel qu'affiché, hors remise.
        return when (sort) {
            CatalogSort.ServerOrder -> matching
            CatalogSort.PriceAscending -> matching.sortedBy { it.price }
            CatalogSort.PriceDescending -> matching.sortedByDescending { it.price }
            CatalogSort.BestRated -> matching.sortedByDescending { it.rating }
        }
    }

    companion object {
        const val TOP_RATED_MIN = 4.0
    }
}

/**
 * Résultat d'une recherche : [products] à afficher, et [catalog] le cache complet (pagination, date de mise à jour,
 * message « aucun produit » ou « aucun résultat »).
 */
data class FilteredCatalog(val products: List<ProductSummary>, val catalog: Catalog)
