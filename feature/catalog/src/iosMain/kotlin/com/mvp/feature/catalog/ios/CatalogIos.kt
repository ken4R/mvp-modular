package com.mvp.feature.catalog.ios

import com.mvp.feature.catalog.domain.model.CatalogFilter
import com.mvp.feature.catalog.domain.model.CatalogSort

/**
 * Filtre du catalogue construit depuis Swift : [sort] est le nom Kotlin de l'entrée de `CatalogSort`
 * (« ServerOrder », « PriceAscending », « PriceDescending », « BestRated ») ; un nom inconnu vaut « ServerOrder ».
 */
fun catalogFilter(query: String, topRatedOnly: Boolean, sort: String): CatalogFilter = CatalogFilter(
    query = query,
    topRatedOnly = topRatedOnly,
    sort = CatalogSort.entries.firstOrNull { it.name == sort } ?: CatalogSort.ServerOrder,
)
