package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.FlowUseCase
import com.mvp.feature.todo.domain.model.TodoAttachment
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Pièces jointes d'une tâche, dans l'ordre d'ajout. Paramètre : id de la tâche. */
class ObserveTodoAttachmentsUseCase(
    private val repository: TodoRepository,
    dispatcher: CoroutineDispatcher,
) : FlowUseCase<String, List<TodoAttachment>>(dispatcher) {

    override fun execute(params: String): Flow<AppResult<List<TodoAttachment>>> =
        repository.observeAttachments(params).map { AppResult.Success(it) }
}
