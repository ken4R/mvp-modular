package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoNote
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Enregistre la note d'une tâche (vide pour la retirer). Règle métier : 2 000 caractères max, espaces de début
 * et de fin retirés. La note est synchronisée avec la tâche (ADR 0014).
 */
class UpdateTodoNoteUseCase(
    private val repository: TodoRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<TodoNote, Todo>(dispatcher) {

    override suspend fun execute(params: TodoNote): AppResult<Todo> {
        val note = params.note.trim()
        if (note.length > MAX_NOTE_LENGTH) {
            return AppResult.Failure(AppError.Validation("$MAX_NOTE_LENGTH caractères maximum"))
        }
        return repository.setNote(params.todoId, note)
    }

    companion object {
        const val MAX_NOTE_LENGTH = 2_000
    }
}
