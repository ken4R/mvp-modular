package com.mvp.feature.todo.domain.model

/** Tâche dans la corbeille (ADR 0025), lue par le dépôt. [trashedAt] : heure de la mise à la corbeille (epoch ms). */
data class TrashedTodo(val todo: Todo, val trashedAt: Long)

/**
 * Ligne de l'écran Corbeille. [daysLeft] : jours avant la suppression définitive, arrondis au jour supérieur (1 à
 * [TodoTrash.RETENTION_DAYS]) ; une tâche dont le délai est écoulé n'est plus listée.
 */
data class TrashItem(val todo: Todo, val daysLeft: Int)

/** Règles de la corbeille (ADR 0025). */
object TodoTrash {
    /** Une tâche reste restaurable pendant 30 jours après sa mise à la corbeille. */
    const val RETENTION_DAYS = 30
    const val DAY_MILLIS = 24L * 60 * 60 * 1000
    const val RETENTION_MILLIS = RETENTION_DAYS * DAY_MILLIS

    /** Jours restants à [now] pour une tâche jetée à [trashedAt] ; 0 si elle doit être supprimée définitivement. */
    fun daysLeft(trashedAt: Long, now: Long): Int {
        // Par l'écart, sans jamais calculer trashedAt + délai (débordement avec une heure reçue démesurée).
        val elapsed = now - trashedAt
        if (elapsed >= RETENTION_MILLIS) return 0
        // Jetée « dans le futur » (horloge reculée, heure reçue en avance) : jamais plus que le délai complet.
        if (elapsed <= 0) return RETENTION_DAYS
        val left = RETENTION_MILLIS - elapsed
        return ((left + DAY_MILLIS - 1) / DAY_MILLIS).toInt()
    }

    /** Une tâche jetée au plus tard à cette heure a fait son temps à [now]. */
    fun expiryCutoff(now: Long): Long = now - RETENTION_MILLIS
}
