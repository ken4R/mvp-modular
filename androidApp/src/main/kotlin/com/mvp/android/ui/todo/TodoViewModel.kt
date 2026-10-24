package com.mvp.android.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mvp.core.common.domain.error.AppError
import com.mvp.core.common.domain.result.AppResult
import com.mvp.core.common.domain.result.onFailure
import com.mvp.core.common.domain.result.onSuccess
import com.mvp.core.common.domain.usecase.NoParams
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoFilter
import com.mvp.feature.todo.domain.model.TodoReminder
import com.mvp.feature.todo.domain.model.TodoStatusFilter
import com.mvp.feature.todo.domain.model.TodoSyncStatus
import com.mvp.feature.todo.domain.usecase.AddTodoUseCase
import com.mvp.feature.todo.domain.usecase.DeleteTodoUseCase
import com.mvp.feature.todo.domain.usecase.FindTodoUseCase
import com.mvp.feature.todo.domain.usecase.ObserveFilteredTodosUseCase
import com.mvp.feature.todo.domain.usecase.ObserveRemindersEnabledUseCase
import com.mvp.feature.todo.domain.usecase.ObserveTodoSyncStatusUseCase
import com.mvp.feature.todo.domain.usecase.SetTodoReminderUseCase
import com.mvp.feature.todo.domain.usecase.SyncTodosUseCase
import com.mvp.feature.todo.domain.usecase.ToggleTodoUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodoUiState(
    /** Tâches qui correspondent à la recherche et aux filtres. */
    val todos: List<Todo> = emptyList(),
    /** Toutes les tâches, sans filtre. */
    val allTodos: List<Todo> = emptyList(),
    /** Texte du champ de recherche, appliqué à [filter] après une courte pause de saisie. */
    val query: String = "",
    val filter: TodoFilter = TodoFilter(),
    val input: String = "",
    val error: String? = null,
    val remindersEnabled: Boolean = true,
    /** Tâche ouverte par un lien : la liste défile jusqu'à elle et la met en évidence quelques secondes. */
    val focusedTodoId: String? = null,
    /** Mode hors ligne (ADR 0012) : `null` tant que l'état n'est pas connu. */
    val sync: TodoSyncStatus? = null,
) {
    /** Bandeau au-dessus de la liste : hors ligne, ou synchronisation en échec. `null` : rien à signaler. */
    val syncBanner: SyncBanner?
        get() {
            val sync = sync ?: return null
            val pending = pendingText(sync.pendingChanges)
            return when {
                !sync.isOnline && sync.pendingChanges > 0 -> SyncBanner("Hors ligne · $pending", canRetry = false)
                !sync.isOnline -> SyncBanner("Hors ligne · vos tâches restent modifiables", canRetry = false)
                sync.lastSyncFailed && sync.pendingChanges > 0 ->
                    SyncBanner("Synchronisation impossible · $pending", canRetry = !sync.isSyncing)
                else -> null
            }
        }

    /** Des rappels à venir sont posés mais ne partiront pas : l'écran invite à activer les notifications. */
    val showRemindersDisabledHint: Boolean
        get() {
            val now = System.currentTimeMillis()
            return !remindersEnabled && allTodos.any { todo -> !todo.isDone && todo.reminderAt.let { it != null && it > now } }
        }
}

/** [canRetry] : bouton « Réessayer » (en ligne, aucune tentative en cours). */
data class SyncBanner(val message: String, val canRetry: Boolean)

private fun pendingText(count: Int): String =
    if (count == 1) "1 modification en attente" else "$count modifications en attente"

@OptIn(ExperimentalCoroutinesApi::class)
class TodoViewModel(
    observeFilteredTodos: ObserveFilteredTodosUseCase,
    observeRemindersEnabled: ObserveRemindersEnabledUseCase,
    observeSyncStatus: ObserveTodoSyncStatusUseCase,
    private val syncTodos: SyncTodosUseCase,
    private val addTodo: AddTodoUseCase,
    private val toggleTodo: ToggleTodoUseCase,
    private val deleteTodo: DeleteTodoUseCase,
    private val findTodo: FindTodoUseCase,
    private val setTodoReminder: SetTodoReminderUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TodoUiState())
    private var queryJob: Job? = null
    val state: StateFlow<TodoUiState> = _state.asStateFlow()

    init {
        // Chaque nouveau filtre remplace l'observation précédente.
        _state.map { it.filter }
            .distinctUntilChanged()
            .flatMapLatest { filter -> observeFilteredTodos(filter) }
            .onEach { result ->
                when (result) {
                    is AppResult.Success -> _state.update { it.copy(todos = result.data.todos, allTodos = result.data.all) }
                    is AppResult.Failure -> showError(result.error)
                }
            }
            .launchIn(viewModelScope)
        observeRemindersEnabled(NoParams)
            .onEach { result -> if (result is AppResult.Success) _state.update { it.copy(remindersEnabled = result.data) } }
            .launchIn(viewModelScope)
        observeSyncStatus(NoParams)
            .onEach { result -> if (result is AppResult.Success) _state.update { it.copy(sync = result.data) } }
            .launchIn(viewModelScope)
    }

    /** Bouton « Réessayer » du bandeau ; sinon la synchronisation réessaie seule, de plus en plus espacée. */
    fun onRetrySync() = viewModelScope.launch { syncTodos(NoParams) }

    /**
     * Un lien demande la tâche [id] : mise en évidence si elle existe, les filtres retirés pour qu'elle soit dans la
     * liste ; sinon l'écran reste sur la liste et le dit.
     */
    fun focus(id: String) = viewModelScope.launch {
        findTodo(id)
            .onSuccess { todo ->
                queryJob?.cancel()
                _state.update { it.copy(focusedTodoId = todo.id, query = "", filter = TodoFilter()) }
            }
            .onFailure(::showError)
    }

    fun clearFocus() = _state.update { it.copy(focusedTodoId = null) }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            updateFilter { it.copy(query = query) }
        }
    }

    fun onStatusFilterChange(status: TodoStatusFilter) = updateFilter { it.copy(status = status) }

    fun onToggleReminderFilter() = updateFilter { it.copy(withReminder = !it.withReminder) }

    fun onToggleAttachmentFilter() = updateFilter { it.copy(withAttachments = !it.withAttachments) }

    fun onClearFilter() {
        queryJob?.cancel()
        _state.update { it.copy(query = "", filter = TodoFilter()) }
    }

    private fun updateFilter(transform: (TodoFilter) -> TodoFilter) = _state.update { it.copy(filter = transform(it.filter)) }

    fun onInputChange(value: String) = _state.update { it.copy(input = value, error = null) }

    fun onAdd() = viewModelScope.launch {
        addTodo(_state.value.input)
            .onSuccess { _state.update { it.copy(input = "", error = null) } }
            .onFailure(::showError)
    }

    fun onToggle(id: String) = viewModelScope.launch { toggleTodo(id).onFailure(::showError) }

    fun onDelete(id: String) = viewModelScope.launch {
        if (_state.value.focusedTodoId == id) clearFocus()
        deleteTodo(id).onFailure(::showError)
    }

    /** [at] : heure choisie (epoch ms), `null` pour retirer le rappel. */
    fun onSetReminder(id: String, at: Long?) = viewModelScope.launch {
        setTodoReminder(TodoReminder(id, at))
            .onSuccess { _state.update { it.copy(error = null) } }
            .onFailure(::showError)
    }

    private fun showError(error: AppError) = _state.update { it.copy(error = error.toMessage()) }
}

/** Pause de saisie avant d'appliquer la recherche. */
private const val SEARCH_DEBOUNCE_MILLIS = 250L

private fun AppError.toMessage(): String = when (this) {
    is AppError.Validation -> message
    AppError.NotFound -> "Tâche introuvable"
    AppError.Network -> "Problème de connexion"
    AppError.Unauthorized -> "Accès refusé"
    AppError.PermissionDenied -> "Autorisation refusée"
    AppError.Unavailable -> "Service indisponible"
    is AppError.Unknown -> message ?: "Erreur inattendue"
}
