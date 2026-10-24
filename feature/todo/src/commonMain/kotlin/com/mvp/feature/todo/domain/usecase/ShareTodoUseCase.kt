package com.mvp.feature.todo.domain.usecase

import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.usecase.UseCase
import com.mvp.core.deeplink.domain.DeepLinks
import com.mvp.core.deeplink.domain.model.DeepLink
import com.mvp.feature.todo.domain.model.TodoShare
import com.mvp.feature.todo.domain.model.TodoShareRequest
import com.mvp.feature.todo.domain.repository.TodoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first

/**
 * Texte à partager pour une tâche (ADR 0017) : titre, état, la note seulement si elle est demandée, et le lien
 * profond de la tâche. Règles métier : ni pièce jointe, ni rappel, ni identifiant de compte ; une tâche absente
 * renvoie [AppError.NotFound]. Le lien ne fait que naviguer, après la connexion et le verrou (ADR 0008).
 */
class ShareTodoUseCase(
    private val repository: TodoRepository,
    dispatcher: CoroutineDispatcher,
) : UseCase<TodoShareRequest, TodoShare>(dispatcher) {

    override suspend fun execute(params: TodoShareRequest): AppResult<TodoShare> {
        val todo = repository.observeTodos().first().find { it.id == params.todoId }
            ?: return AppResult.Failure(AppError.NotFound)
        val link = DeepLinks.url(DeepLink.Tasks(todo.id))
        val title = todo.title.oneLine()
        val text = buildString {
            appendLine("Tâche : $title")
            appendLine(if (todo.isDone) "État : terminée" else "État : à faire")
            if (params.includeNote && todo.note.isNotBlank()) {
                appendLine()
                appendLine("Note :")
                appendLine(todo.note)
            }
            appendLine()
            append("Ouvrir dans MVP Modular : $link")
        }
        return AppResult.Success(TodoShare(subject = "Tâche : $title", text = text, link = link))
    }

    // Le titre sert aussi d'objet de mail : une seule ligne.
    private fun String.oneLine(): String = lines().joinToString(" ") { it.trim() }.trim()
}
