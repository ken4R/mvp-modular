package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.repository.TodoTrashRepository
import kotlinx.coroutines.CoroutineDispatcher

/** « Vider la corbeille » (ADR 0025) : tout ce qu'elle contient est supprimé définitivement. Renvoie le nombre de tâches. */
class EmptyTrashUseCase(
    private val repository: TodoTrashRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<NoParams, Int>(dispatcher) {

    override suspend fun execute(params: NoParams): AppResult<Int> = repository.deleteTrashedBefore(Long.MAX_VALUE)
}
