package com.mvp.android.ui.todo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.onFailure
import com.mvp.core.common.domain.result.onSuccess
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoAttachment
import com.mvp.feature.todo.domain.model.TodoCalendarEvent
import com.mvp.feature.todo.domain.model.TodoDueDate
import com.mvp.feature.todo.domain.model.TodoNote
import com.mvp.feature.todo.domain.model.TodoShare
import com.mvp.feature.todo.domain.model.TodoShareRequest
import com.mvp.feature.todo.domain.usecase.AddTodoAttachmentUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoAttachmentUseCase
import com.mvp.feature.todo.domain.usecase.ExportTodoToCalendarUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoAttachmentsUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodosUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoDueDateUseCase
import com.mvp.feature.todo.domain.usecase.ShareTodoUseCase
import com.mvp.feature.todo.domain.usecase.UpdateTodoNoteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

data class TodoDetailUiState(
    /** `null` tant que la tâche n'est pas lue, ou si elle a été supprimée entre-temps (voir [isMissing]). */
    val todo: Todo? = null,
    val isMissing: Boolean = false,
    val noteDraft: String = "",
    val attachments: List<TodoAttachment> = emptyList(),
    /** Pièce jointe affichée en grand dans la fiche. */
    val previewed: TodoAttachment? = null,
    val isAdding: Boolean = false,
    /** Question « inclure la note ? » affichée avant le partage (seulement si la tâche a une note). */
    val askIncludeNote: Boolean = false,
    /** Contenu prêt pour la feuille de partage : l'écran l'ouvre une fois, puis appelle `onShareShown`. */
    val share: TodoShare? = null,
    /** Événement prêt pour l'écran d'ajout du calendrier (ADR 0022) : ouvert une fois, puis `onCalendarEventShown`. */
    val calendarEvent: TodoCalendarEvent? = null,
    val error: String? = null,
) {
    val noteChanged: Boolean get() = todo != null && noteDraft.trim() != todo.note
    val canAddAttachment: Boolean get() = !isAdding && attachments.size < AddTodoAttachmentUseCase.MAX_ATTACHMENTS_PER_TODO
}

/**
 * Fiche d'une tâche : note, pièces jointes (ADR 0014) et échéance (ADR 0022). Une seule instance pour l'onglet Tâches : [open] la
 * remplit pour une tâche, [close] enregistre la note modifiée puis la vide.
 */
class TodoDetailViewModel(
    private val observeTodos: ObserveTodosUseCase,
    private val observeAttachments: ObserveTodoAttachmentsUseCase,
    private val updateNote: UpdateTodoNoteUseCase,
    private val addAttachment: AddTodoAttachmentUseCase,
    private val deleteAttachment: DeleteTodoAttachmentUseCase,
    private val shareTodo: ShareTodoUseCase,
    private val setDueDate: SetTodoDueDateUseCase,
    private val exportToCalendar: ExportTodoToCalendarUseCase,
    private val reader: AttachmentReader,
) : ViewModel() {

    private val _state = MutableStateFlow(TodoDetailUiState())
    val state: StateFlow<TodoDetailUiState> = _state.asStateFlow()

    private var openedId: String? = null
    private var observers: List<Job> = emptyList()

    /** Rouvrir la même tâche (retour sur l'onglet, rotation) garde le brouillon de la note. */
    fun open(todoId: String) {
        if (openedId == todoId) return
        saveChangedNote()
        observers.forEach { it.cancel() }
        openedId = todoId
        _state.value = TodoDetailUiState()
        var noteLoaded = false
        observers = listOf(
            observeTodos(NoParams).onEach { result ->
                if (result !is AppResult.Success) return@onEach
                val todo = result.data.find { it.id == todoId }
                _state.update { current ->
                    // Le brouillon part de la note enregistrée, une seule fois : une synchronisation ne l'écrase pas.
                    val draft = if (!noteLoaded && todo != null) todo.note.also { noteLoaded = true } else current.noteDraft
                    current.copy(todo = todo, isMissing = todo == null, noteDraft = draft)
                }
            }.launchIn(viewModelScope),
            observeAttachments(todoId).onEach { result ->
                if (result !is AppResult.Success) return@onEach
                _state.update { current ->
                    val previewed = current.previewed?.takeIf { shown -> result.data.any { it.id == shown.id } }
                    current.copy(attachments = result.data, previewed = previewed)
                }
            }.launchIn(viewModelScope),
        )
    }

    /** Fermeture de la fiche : la note modifiée est enregistrée (dans viewModelScope, qui survit à l'écran). */
    fun close() {
        saveChangedNote()
        observers.forEach { it.cancel() }
        observers = emptyList()
        openedId = null
        _state.value = TodoDetailUiState()
    }

    /** Enregistre la note modifiée sans fermer la fiche : app en arrière-plan, changement d'onglet. */
    fun saveChangedNote() {
        val current = _state.value
        val todo = current.todo ?: return
        if (current.noteChanged) viewModelScope.launch { updateNote(TodoNote(todo.id, current.noteDraft)) }
    }

    fun onNoteChange(value: String) = _state.update { it.copy(noteDraft = value, error = null) }

    fun onSaveNote() = viewModelScope.launch {
        val todo = _state.value.todo ?: return@launch
        updateNote(TodoNote(todo.id, _state.value.noteDraft))
            .onSuccess { saved -> _state.update { it.copy(noteDraft = saved.note, error = null) } }
            .onFailure(::showError)
    }

    /** Fichier ou photo choisi dans un sélecteur système ; les photos sont réenregistrées en JPEG (sans métadonnées). */
    fun onFilePicked(uri: Uri) = viewModelScope.launch {
        val todoId = openedId ?: return@launch
        // Un ajout à la fois : la limite de pièces jointes est vérifiée avant l'écriture.
        if (_state.value.isAdding) return@launch
        _state.update { it.copy(isAdding = true, error = null) }
        val picked = try {
            withContext(Dispatchers.IO) { reader.read(uri, todoId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (picked == null) {
            _state.update { it.copy(isAdding = false, error = "Impossible de lire ce fichier") }
            return@launch
        }
        addAttachment(picked)
            .onSuccess { _state.update { it.copy(error = null) } }
            .onFailure(::showError)
        _state.update { it.copy(isAdding = false) }
    }

    fun onDeleteAttachment(id: String) = viewModelScope.launch { deleteAttachment(id).onFailure(::showError) }

    fun onPreview(attachment: TodoAttachment?) = _state.update { it.copy(previewed = attachment) }

    /** « Partager » (ADR 0017) : la note n'est jamais partagée sans le demander. */
    fun onShare() {
        val current = _state.value
        if (current.todo?.note.isNullOrBlank() && current.noteDraft.isBlank()) {
            onShareConfirmed(includeNote = false)
        } else {
            _state.update { it.copy(askIncludeNote = true, error = null) }
        }
    }

    fun onShareCancelled() = _state.update { it.copy(askIncludeNote = false) }

    /**
     * Le texte partagé part de la note enregistrée : avec la note, un brouillon modifié est enregistré avant. Sans
     * la note, le brouillon n'est pas touché (il l'est à la fermeture de la fiche, comme d'habitude).
     */
    fun onShareConfirmed(includeNote: Boolean) = viewModelScope.launch {
        _state.update { it.copy(askIncludeNote = false) }
        val current = _state.value
        val todo = current.todo ?: return@launch
        if (includeNote && current.noteChanged) {
            val saved = updateNote(TodoNote(todo.id, current.noteDraft))
            if (saved is AppResult.Failure) {
                showError(saved.error)
                return@launch
            }
            saved.onSuccess { note -> _state.update { it.copy(noteDraft = note.note) } }
        }
        shareTodo(TodoShareRequest(todo.id, includeNote))
            .onSuccess { share -> _state.update { it.copy(share = share, error = null) } }
            .onFailure(::showError)
    }

    fun onShareShown() = _state.update { it.copy(share = null) }

    /** [dueDate] en jours depuis le 1970-01-01, `null` pour retirer l'échéance. */
    fun onSetDueDate(dueDate: Int?) = viewModelScope.launch {
        val todoId = openedId ?: return@launch
        setDueDate(TodoDueDate(todoId, dueDate))
            .onSuccess { _state.update { it.copy(error = null) } }
            .onFailure(::showError)
    }

    /** « Ajouter au calendrier » : l'écran d'ajout du calendrier du téléphone, à confirmer par l'utilisateur. */
    fun onAddToCalendar() = viewModelScope.launch {
        val todoId = openedId ?: return@launch
        exportToCalendar(todoId)
            .onSuccess { event -> _state.update { it.copy(calendarEvent = event, error = null) } }
            .onFailure(::showError)
    }

    fun onCalendarEventShown() = _state.update { it.copy(calendarEvent = null) }

    fun onCalendarUnavailable() = _state.update { it.copy(calendarEvent = null, error = "Aucune application de calendrier") }

    private fun showError(error: AppError) = _state.update { it.copy(error = error.toDetailMessage()) }
}

private fun AppError.toDetailMessage(): String = when (this) {
    is AppError.Validation -> message
    AppError.NotFound -> "Tâche introuvable"
    is AppError.Unknown -> "Enregistrement impossible, vérifiez l'espace disponible"
    else -> "Erreur inattendue"
}
