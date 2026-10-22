package com.mvp.core.deeplink.domain.model

/**
 * Destination d'un lien profond. Un lien ne fait que naviguer : il n'ajoute, ne coche ni ne supprime rien.
 * Les identifiants viennent de l'extérieur : l'écran visé vérifie qu'ils existent encore.
 */
sealed interface DeepLink {
    /** Onglet Tâches, et la tâche [todoId] mise en évidence si elle est donnée. */
    data class Tasks(val todoId: String? = null) : DeepLink

    /** Onglet Météo. */
    data object Weather : DeepLink

    /** Onglet Catalogue, et la fiche du produit [productId] si elle est donnée. */
    data class Catalog(val productId: Long? = null) : DeepLink

    /** Onglet Réglages. */
    data object Settings : DeepLink
}
