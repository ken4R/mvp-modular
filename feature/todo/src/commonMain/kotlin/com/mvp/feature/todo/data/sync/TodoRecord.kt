package com.mvp.feature.todo.data.sync

/**
 * Version d'une tâche échangée avec le serveur (ADR 0012). Le rappel n'en fait pas partie : il reste propre
 * à l'appareil. [updatedAt] : heure de la modification (epoch ms), qui départage deux versions (la plus récente
 * gagne). [isDeleted] : tâche supprimée ; le serveur en garde la trace pour que la suppression atteigne les
 * autres appareils et qu'une version plus ancienne ne la fasse pas réapparaître.
 */
internal data class TodoRecord(
    val id: String,
    val title: String,
    val isDone: Boolean,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)
