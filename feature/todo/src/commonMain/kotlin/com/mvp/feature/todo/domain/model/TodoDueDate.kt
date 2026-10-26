package com.mvp.feature.todo.domain.model

/**
 * Échéance à poser sur la tâche [todoId] : [dueDate] en jours depuis le 1970-01-01
 * ([com.mvp.feature.todo.domain.calendar.CalendarDate.epochDay]), `null` pour la retirer (ADR 0022).
 */
data class TodoDueDate(val todoId: String, val dueDate: Int?)
