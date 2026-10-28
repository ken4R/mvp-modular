package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.model.TodoTrash
import com.mvp.feature.todo.domain.model.TrashItem
import com.mvp.feature.todo.domain.repository.TodoTrashRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Écran Corbeille (ADR 0025) : les tâches jetées, la dernière en premier, avec les jours qui restent avant leur
 * suppression définitive. Recalculé à chaque changement et toutes les heures, même si l'écran reste ouvert ; une
 * tâche dont le délai est écoulé disparaît de la liste (elle sera supprimée par [PurgeExpiredTrashUseCase]).
 * [now] : heure courante (epoch ms), lue à chaque calcul.
 */
class ObserveTrashUseCase(
    private val repository: TodoTrashRepository,
    private val now: () -> Long,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<NoParams, List<TrashItem>>(dispatcher) {

    override fun execute(params: NoParams): Flow<AppResult<List<TrashItem>>> =
        combine(repository.observeTrash(), ticks()) { trash, _ ->
            val current = now()
            val items = trash.mapNotNull { trashed ->
                TodoTrash.daysLeft(trashed.trashedAt, current).takeIf { it > 0 }?.let { TrashItem(trashed.todo, it) }
            }
            AppResult.Success(items)
        }.distinctUntilChanged()

    private fun ticks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(REFRESH_MILLIS)
        }
    }

    private companion object {
        const val REFRESH_MILLIS = 60L * 60 * 1000
    }
}
