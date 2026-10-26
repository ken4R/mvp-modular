package com.mvp.feature.todo.data.sync

import kotlinx.coroutines.flow.Flow

/**
 * Ce que la synchronisation lit et écrit dans la base locale (ADR 0012). Implémenté par
 * [com.mvp.feature.todo.data.source.SqlDelightTodoLocalDataSource] : chaque modification d'une tâche y entre
 * dans la file d'envoi au sein de la même transaction, donc rien ne se perd si l'app est tuée entre les deux.
 */
internal interface TodoSyncLocalDataSource {

    /** Nombre de tâches modifiées hors ligne (ou pas encore envoyées), réémis à chaque écriture dans la file. */
    fun observePendingCount(): Flow<Int>

    /** Les modifications à envoyer, la plus ancienne d'abord. */
    fun pendingChanges(): List<TodoRecord>

    /**
     * Retire de la file les modifications [sent], acceptées par le serveur. Une tâche modifiée à nouveau
     * pendant l'envoi reste dans la file, avec sa nouvelle version.
     */
    fun markSent(sent: List<TodoRecord>)

    /**
     * Applique les versions reçues du serveur : une tâche est créée, mise à jour ou supprimée si la version
     * reçue n'est pas plus ancienne que la sienne (à égalité, le serveur l'emporte). Une tâche qui a une modification en attente n'est pas touchée :
     * sa version part au prochain envoi et le serveur départage. Les rappels et les pièces jointes de l'appareil
     * sont gardés (une tâche supprimée perd ses pièces jointes). Une version au titre vide ou trop long (règles
     * d'AddTodoUseCase), à la note trop longue (UpdateTodoNoteUseCase) ou à l'échéance hors limites (SetTodoDueDateUseCase)
     * est ignorée, comme une version dont l'id n'est pas un id de tâche de l'app (`DeepLinks.isValidTodoId`).
     */
    fun applyRemote(records: List<TodoRecord>)
}
