package com.mvp.feature.todo.domain.reminder

import com.mvp.feature.todo.domain.model.Todo
import kotlinx.coroutines.flow.Flow

/** Rappels par lieu du système (implémentés dans data, sur `core:notifications` ; ADR 0023). */
interface LocationReminderScheduler {

    /** `true` si le système peut les déclencher (permissions de localisation) ; relu à chaque [refreshAvailability]. */
    fun observeAvailability(): Flow<Boolean>

    /** Relit les permissions : accordées dans les paramètres du système, elles ne préviennent pas l'app. */
    fun refreshAvailability()

    /** Remplace les rappels posés par ceux de [todos] (tâches avec un rappel par lieu), jusqu'à [expiresAt]. */
    suspend fun replaceAll(todos: List<Todo>, expiresAt: Long)

    /** Retire tous les rappels par lieu, posés ou déjà affichés (déconnexion). */
    suspend fun cancelAll()
}
