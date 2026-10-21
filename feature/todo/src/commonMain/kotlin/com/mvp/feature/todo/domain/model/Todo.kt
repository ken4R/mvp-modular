package com.mvp.feature.todo.domain.model

/** [reminderAt] : heure du rappel en millisecondes depuis l'epoch (UTC), `null` sans rappel. */
data class Todo(
    val id: String,
    val title: String,
    val isDone: Boolean,
    val reminderAt: Long? = null,
)
