package com.mvp.feature.todo.domain.model

/**
 * Ce que le widget Tâches de l'écran d'accueil peut afficher. Le widget est visible hors de l'app (écran
 * d'accueil, et sur iOS l'écran verrouillé en mode En veille) : il ne montre une tâche qu'avec une session
 * valide et le verrou Face ID / empreinte coupé. Voir docs/adr/0011.
 */
sealed interface TodoWidgetState {

    /** Pas de session valide : le widget invite à se connecter, sans aucune donnée. */
    data object SignedOut : TodoWidgetState

    /** Déverrouillage Face ID / empreinte activé : le widget invite à ouvrir l'app, sans aucune donnée. */
    data object Locked : TodoWidgetState

    /**
     * Tâches à faire. [remainingCount] compte toutes les tâches non terminées, [items] n'en garde que
     * les premières (rappel le plus proche d'abord). [visibleUntil] (epoch ms) : fin de la session, après
     * laquelle le widget ne doit plus rien montrer même si l'app n'a pas été rouverte.
     */
    data class Tasks(
        val remainingCount: Int,
        val items: List<TodoWidgetItem>,
        val visibleUntil: Long,
        /** Lien qui ouvre l'onglet Tâches. */
        val link: String,
    ) : TodoWidgetState
}

/** Une tâche du widget. [link] ouvre l'app sur cette tâche (format de core:deeplink). */
data class TodoWidgetItem(
    val id: String,
    val title: String,
    /** Heure du rappel en epoch ms, `null` sans rappel. */
    val reminderAt: Long?,
    val link: String,
)
