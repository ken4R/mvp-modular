package com.mvp.feature.todo.domain.repository

import kotlinx.coroutines.flow.Flow

/** Données des statistiques (ADR 0019), effacées avec les tâches à la déconnexion. */
interface TodoStatsRepository {
    /** Heures (epoch ms) auxquelles les tâches encore présentes et terminées l'ont été, dans un ordre quelconque. */
    fun observeCompletionTimes(): Flow<List<Long>>
}
