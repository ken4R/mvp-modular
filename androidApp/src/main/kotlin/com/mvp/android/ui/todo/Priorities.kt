package com.mvp.android.ui.todo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mvp.feature.todo.domain.model.TodoPriority

/** Libellés de la priorité (ADR 0024), dans l'ordre du sélecteur de la fiche. */
internal val PRIORITY_LABELS = listOf(
    TodoPriority.None to "Aucune",
    TodoPriority.Low to "Basse",
    TodoPriority.Medium to "Moyenne",
    TodoPriority.High to "Haute",
)

/** « Priorité haute » sous le titre dans la liste ; `null` sans priorité. */
internal fun priorityBadge(priority: TodoPriority): String? = when (priority) {
    TodoPriority.None -> null
    TodoPriority.Low -> "Priorité basse"
    TodoPriority.Medium -> "Priorité moyenne"
    TodoPriority.High -> "Priorité haute"
}

/** Couleur du libellé de priorité : celle des erreurs pour la haute, l'accent pour la moyenne. */
@Composable
internal fun priorityColor(priority: TodoPriority): Color = when (priority) {
    TodoPriority.High -> MaterialTheme.colorScheme.error
    TodoPriority.Medium -> MaterialTheme.colorScheme.primary
    TodoPriority.Low, TodoPriority.None -> MaterialTheme.colorScheme.onSurface
}
