package com.mvp.feature.catalog.domain.model

/** Pages du catalogue déjà chargées (et gardées hors ligne). */
data class Catalog(
    val products: List<ProductSummary>,
    /** Nombre total de produits côté serveur, 0 tant qu'aucune page n'a été chargée. */
    val total: Int,
    /** Produits déjà parcourus côté serveur : rang de la prochaine page (peut dépasser [products] en cas de doublon). */
    val loadedCount: Int,
    /** Date du dernier chargement réussi (epoch ms), `null` si le catalogue n'a jamais été chargé. */
    val updatedAt: Long?,
) {
    /** `true` s'il reste des produits à charger (ou si rien n'a encore été chargé). */
    val hasMore: Boolean get() = updatedAt == null || loadedCount < total
}
