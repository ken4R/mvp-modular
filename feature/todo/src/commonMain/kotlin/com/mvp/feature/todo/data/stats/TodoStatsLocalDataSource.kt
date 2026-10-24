package com.mvp.feature.todo.data.stats

import kotlinx.coroutines.flow.Flow

/** Heures de fin des tâches, gardées par [com.mvp.feature.todo.data.source.SqlDelightTodoLocalDataSource]. */
internal interface TodoStatsLocalDataSource {
    fun observeCompletionTimes(): Flow<List<Long>>
}
