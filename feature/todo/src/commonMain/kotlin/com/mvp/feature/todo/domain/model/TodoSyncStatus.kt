package com.mvp.feature.todo.domain.model

/**
 * Où en est l'envoi des tâches vers le serveur, tel que l'écran Tâches l'affiche (ADR 0012).
 * [pendingChanges] : tâches modifiées sur cet appareil et pas encore envoyées. [lastSyncFailed] : la dernière
 * tentative a échoué (elle est retentée toute seule tant que l'appareil est en ligne).
 */
data class TodoSyncStatus(
    val isOnline: Boolean,
    val pendingChanges: Int,
    val isSyncing: Boolean,
    val lastSyncFailed: Boolean,
)

/** État de la synchronisation connu du repository, sans le réseau. */
data class TodoSyncState(
    val pendingChanges: Int = 0,
    val isSyncing: Boolean = false,
    val lastSyncFailed: Boolean = false,
)
