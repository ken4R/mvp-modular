package com.mvp.feature.todo.data.stats

import com.mvp.feature.todo.domain.repository.TodoStatsRepository
import kotlinx.coroutines.flow.Flow

internal class TodoStatsRepositoryImpl(private val local: TodoStatsLocalDataSource) : TodoStatsRepository {
    override fun observeCompletionTimes(): Flow<List<Long>> = local.observeCompletionTimes()
}
