package com.mvp.feature.todo.domain.model

/**
 * [reminderAt] : heure du rappel en millisecondes depuis l'epoch (UTC), `null` sans rappel.
 * [note] : texte libre, vide sans note. [attachmentCount] : nombre de pièces jointes (ADR 0014).
 * [dueDate] : jour d'échéance en jours depuis le 1970-01-01 (date du calendrier, sans heure ni fuseau, voir
 * [com.mvp.feature.todo.domain.calendar.CalendarDate]), `null` sans échéance (ADR 0022).
 */
data class Todo(
    val id: String,
    val title: String,
    val isDone: Boolean,
    val reminderAt: Long? = null,
    val note: String = "",
    val attachmentCount: Int = 0,
    val dueDate: Int? = null,
) {
    /** Règle du retard (ADR 0022) : tâche à faire dont l'échéance est avant [today] (jours depuis le 1970-01-01). */
    fun isOverdue(today: Int): Boolean = !isDone && dueDate?.let { it < today } == true
}
