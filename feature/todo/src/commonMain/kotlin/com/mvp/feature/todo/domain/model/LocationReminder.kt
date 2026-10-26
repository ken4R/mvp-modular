package com.mvp.feature.todo.domain.model

/**
 * Rappel par lieu (ADR 0023) : à chaque arrivée dans le cercle ([latitude], [longitude], [radiusMeters]) tant que la
 * tâche est à faire. [label] : nom donné par l'utilisateur (« Bureau »). Gardé sur l'appareil, jamais synchronisé.
 */
data class LocationReminder(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val label: String,
) {
    companion object {
        /** Rayons proposés, en mètres : sous 100 m, les zones se déclenchent mal sur les deux plateformes. */
        val RADII = listOf(100, 200, 500)
        const val DEFAULT_RADIUS = 200
        const val MAX_LABEL_LENGTH = 40
        const val DEFAULT_LABEL = "Ce lieu"

        /** iOS surveille au plus 20 zones par app : au plus 20 tâches à faire avec un rappel par lieu. */
        const val MAX_ACTIVE = 20
    }
}

/** Rappel à poser sur la tâche [todoId] à la position actuelle de l'appareil. [label] vide : [LocationReminder.DEFAULT_LABEL]. */
data class LocationReminderHere(
    val todoId: String,
    val radiusMeters: Int = LocationReminder.DEFAULT_RADIUS,
    val label: String = "",
)
