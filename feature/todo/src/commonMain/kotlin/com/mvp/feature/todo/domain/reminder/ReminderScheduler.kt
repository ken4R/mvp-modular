package com.mvp.feature.todo.domain.reminder

import com.mvp.feature.todo.domain.model.Todo

/** Notifications de rappel du système (implémentées dans data, sur `core:notifications`). */
interface ReminderScheduler {

    /** Remplace tous les rappels programmés par ceux de [todos] (tâches avec un rappel à venir). */
    suspend fun replaceAll(todos: List<Todo>)

    /** Annule tous les rappels, programmés ou déjà affichés (déconnexion). */
    suspend fun cancelAll()
}
