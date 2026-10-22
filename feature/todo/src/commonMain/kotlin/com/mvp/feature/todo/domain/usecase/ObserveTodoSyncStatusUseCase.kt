package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.connectivity.ConnectivityObserver
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.model.TodoSyncStatus
import com.mvp.feature.todo.domain.repository.TodoSyncRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Bandeau de l'écran Tâches : hors ligne ou non, nombre de modifications en attente, synchronisation en
 * cours ou en échec (ADR 0012). ConnectivityObserver vient de core:connectivity, sans dépendance directe.
 */
class ObserveTodoSyncStatusUseCase(
    private val sync: TodoSyncRepository,
    private val connectivity: ConnectivityObserver,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, TodoSyncStatus>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<TodoSyncStatus>> =
        combine(connectivity.observeIsOnline(), sync.observeSyncState()) { online, state ->
            TodoSyncStatus(
                isOnline = online,
                pendingChanges = state.pendingChanges,
                isSyncing = state.isSyncing,
                lastSyncFailed = state.lastSyncFailed,
            )
        }
            .distinctUntilChanged()
            .map { AppResult.Success(it) }
}
