package com.mvp.feature.todo.data.sync

import com.mvp.feature.todo.domain.model.TodoPriority

/**
 * Version d'une tâche échangée avec le serveur (ADR 0012). Le rappel et les pièces jointes n'en font pas
 * partie : ils restent propres à l'appareil. La note (ADR 0014), l'échéance (ADR 0022), la priorité et la clé d'ordre
 * (ADR 0024), elles, en font partie.
 * [updatedAt] : heure de la modification (epoch ms), qui départage deux versions (la plus récente gagne).
 * [isDeleted] : tâche supprimée ; le serveur en garde la trace pour que la suppression atteigne les autres appareils et qu'une version plus ancienne ne la fasse pas réapparaître.
 */
internal data class TodoRecord(
    val id: String,
    val title: String,
    val isDone: Boolean,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val note: String = "",
    /** Jour d'échéance en jours depuis le 1970-01-01, `null` sans échéance. */
    val dueDate: Int? = null,
    val priority: TodoPriority = TodoPriority.None,
    /** Clé d'ordre manuel ([com.mvp.feature.todo.domain.order.OrderKey]) ; `""` : inconnue, la tâche garde la sienne. */
    val position: String = "",
)
