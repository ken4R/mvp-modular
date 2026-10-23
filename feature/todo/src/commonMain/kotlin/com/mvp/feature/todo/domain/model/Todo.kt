package com.mvp.feature.todo.domain.model

/**
 * [reminderAt] : heure du rappel en millisecondes depuis l'epoch (UTC), `null` sans rappel.
 * [note] : texte libre, vide sans note. [attachmentCount] : nombre de pièces jointes (ADR 0014).
 */
data class Todo(
    val id: String,
    val title: String,
    val isDone: Boolean,
    val reminderAt: Long? = null,
    val note: String = "",
    val attachmentCount: Int = 0,
)
