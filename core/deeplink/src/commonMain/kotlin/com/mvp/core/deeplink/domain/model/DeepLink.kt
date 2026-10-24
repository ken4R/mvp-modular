package com.mvp.core.deeplink.domain.model

/**
 * Destination d'un lien profond ou d'un raccourci. Elle ne fait que naviguer : elle n'ajoute, ne coche ni ne supprime rien.
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

    /**
     * Onglet Tâches, le champ « Nouvelle tâche » prérempli avec [title] (raccourci vocal Siri / Assistant, ADR 0018).
     * Jamais lu dans une URL : seul le raccourci de l'app le pose. Rien n'est ajouté sans le bouton « Ajouter ».
     */
    data class NewTask(val title: String) : DeepLink {
        // Le titre dicté reste hors des journaux, même si un lien en attente est un jour journalisé.
        override fun toString() = "NewTask(title=<${title.length} caractères>)"
    }
}
