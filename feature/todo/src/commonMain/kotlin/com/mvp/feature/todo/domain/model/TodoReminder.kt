package com.mvp.feature.todo.domain.model

/** Rappel à poser sur la tâche [todoId] : [at] en millisecondes depuis l'epoch, `null` pour le retirer. */
data class TodoReminder(val todoId: String, val at: Long?)
