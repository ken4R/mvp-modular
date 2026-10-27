package com.mvp.feature.todo.domain.model

/**
 * Priorité d'une tâche (ADR 0024), synchronisée comme le titre. [level] : valeur gardée en base et envoyée au serveur ;
 * plus elle est haute, plus la tâche est prioritaire.
 */
enum class TodoPriority(val level: Int) {
    None(0),
    Low(1),
    Medium(2),
    High(3),
    ;

    companion object {
        /** `null` pour un niveau inconnu (version reçue que l'app n'aurait pas pu écrire). */
        fun fromLevel(level: Int): TodoPriority? = entries.firstOrNull { it.level == level }
    }
}

/** Paramètre de [com.mvp.feature.todo.domain.usecase.SetTodoPriorityUseCase]. */
data class TodoPriorityChange(val todoId: String, val priority: TodoPriority)

/**
 * Paramètre de [com.mvp.feature.todo.domain.usecase.MoveTodoUseCase] : la tâche [todoId] est posée juste après
 * [afterTodoId], la tâche affichée au-dessus d'elle une fois lâchée (`null` : tout en haut). Les tâches à faire et
 * les terminées restent chacune dans leur groupe.
 */
data class TodoMove(val todoId: String, val afterTodoId: String?)
