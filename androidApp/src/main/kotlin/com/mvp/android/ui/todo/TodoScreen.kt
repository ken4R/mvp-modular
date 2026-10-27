package com.mvp.android.ui.todo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvp.android.ui.common.StatusBanner
import com.mvp.android.ui.theme.MvpColors
import com.mvp.android.ui.theme.Radius
import com.mvp.android.ui.theme.Spacing
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todo.domain.model.TodoFilter
import com.mvp.feature.todo.domain.model.TodoStatusFilter
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * [focusRequest] : tâche demandée par un lien, à mettre en évidence ; [onFocusRequestHandled] une fois prise en compte.
 * [draftRequest] : titre dicté par le raccourci vocal (ADR 0018), prérempli dans « Nouvelle tâche » ;
 * [onDraftRequestHandled] une fois pris en compte. La tâche n'est ajoutée qu'avec le bouton « Ajouter ».
 * [onOpenComposeMultiplatform] : affiche un bouton vers la version Compose Multiplatform de l'écran (ADR 0010).
 * Un tap sur une tâche ouvre sa fiche (note et pièces jointes, ADR 0014) à la place de la liste ; le bouton
 * « Statistiques », le graphique des tâches terminées par semaine (ADR 0019) ; le bouton « Calendrier », les tâches par
 * date d'échéance (ADR 0022), d'où une tâche ouverte revient au calendrier ; le bouton « Corbeille », les tâches
 * supprimées, restaurables 30 jours (ADR 0025). Une suppression affiche « Annuler » quelques secondes.
 */
@Composable
fun TodoScreen(
    focusRequest: String? = null,
    onFocusRequestHandled: () -> Unit = {},
    draftRequest: String? = null,
    onDraftRequestHandled: () -> Unit = {},
    onOpenComposeMultiplatform: (() -> Unit)? = null,
    viewModel: TodoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var openedTodoId by rememberSaveable { mutableStateOf<String?>(null) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var showTrash by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    // Un nouveau message remplace le précédent (sa tâche reste dans la corbeille).
    LaunchedEffect(state.trashed) {
        val notice = state.trashed ?: return@LaunchedEffect
        val title = notice.title.ifEmpty { "Tâche" }
        val result = snackbar.showSnackbar(
            message = "« $title » placée dans la corbeille",
            actionLabel = "Annuler",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.onUndoDelete(notice)
        } else {
            viewModel.onTrashedNoticeDismissed(notice)
        }
    }
    LaunchedEffect(focusRequest) {
        focusRequest?.let {
            // Un lien vers une tâche revient à la liste, où elle est mise en évidence.
            openedTodoId = null
            showStats = false
            showCalendar = false
            showTrash = false
            viewModel.focus(it)
        }
        onFocusRequestHandled()
    }
    LaunchedEffect(draftRequest) {
        draftRequest?.let {
            // Le champ « Nouvelle tâche » est sur la liste : on y revient. Un titre vide (raccourci du lanceur)
            // garde le texte déjà saisi ; sinon le titre dicté le remplace.
            openedTodoId = null
            showStats = false
            showCalendar = false
            showTrash = false
            if (it.isNotEmpty()) viewModel.onInputChange(it)
        }
        onDraftRequestHandled()
    }
    openedTodoId?.let { id ->
        TodoDetailScreen(todoId = id, onClose = { openedTodoId = null })
        return
    }
    if (showStats) {
        TodoStatsScreen(onClose = { showStats = false })
        return
    }
    if (showCalendar) {
        TodoCalendarScreen(onClose = { showCalendar = false }, onOpenTodo = { openedTodoId = it })
        return
    }
    if (showTrash) {
        TodoTrashScreen(onClose = { showTrash = false })
        return
    }
    // Défile jusqu'à la tâche mise en évidence, puis retire la mise en évidence.
    val focusIndex = state.focusedTodoId?.let { id -> state.todos.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
    LaunchedEffect(state.focusedTodoId, focusIndex != null) {
        if (focusIndex == null) return@LaunchedEffect
        listState.animateScrollToItem(focusIndex)
        delay(FOCUS_DURATION_MILLIS)
        viewModel.clearFocus()
    }
    TodoContent(
        state = state,
        listState = listState,
        onOpenComposeMultiplatform = onOpenComposeMultiplatform,
        onOpenStats = { showStats = true },
        onOpenCalendar = { showCalendar = true },
        onOpenTrash = { showTrash = true },
        snackbar = snackbar,
        onInputChange = viewModel::onInputChange,
        onAdd = { viewModel.onAdd() },
        onToggle = { viewModel.onToggle(it) },
        onDelete = { viewModel.onDelete(it) },
        onOpen = { openedTodoId = it },
        onPickReminder = { todo -> pickReminder(context, todo.reminderAt) { at -> viewModel.onSetReminder(todo.id, at) } },
        onRemoveReminder = { viewModel.onSetReminder(it, null) },
        onRetrySync = { viewModel.onRetrySync() },
        onDrop = { reordered, movedId -> viewModel.onDrop(reordered, movedId) },
        filterActions = TodoFilterActions(
            onQueryChange = viewModel::onQueryChange,
            onStatusChange = viewModel::onStatusFilterChange,
            onToggleReminder = viewModel::onToggleReminderFilter,
            onToggleAttachments = viewModel::onToggleAttachmentFilter,
            onTogglePrioritySort = viewModel::onTogglePrioritySort,
            onClear = viewModel::onClearFilter,
        ),
    )
}

/** Recherche et filtres de la liste. */
private class TodoFilterActions(
    val onQueryChange: (String) -> Unit,
    val onStatusChange: (TodoStatusFilter) -> Unit,
    val onToggleReminder: () -> Unit,
    val onToggleAttachments: () -> Unit,
    val onTogglePrioritySort: () -> Unit,
    val onClear: () -> Unit,
)

@Composable
private fun TodoContent(
    state: TodoUiState,
    listState: LazyListState,
    onOpenComposeMultiplatform: (() -> Unit)?,
    onOpenStats: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenTrash: () -> Unit,
    snackbar: SnackbarHostState,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
    onPickReminder: (Todo) -> Unit,
    onRemoveReminder: (String) -> Unit,
    onRetrySync: () -> Unit,
    onDrop: (reordered: List<Todo>, movedId: String) -> Unit,
    filterActions: TodoFilterActions,
) {
    // Ordre pendant un glisser-déposer, montré à la place de la liste du ViewModel ; oublié dès qu'elle change
    // après le lâcher (le ViewModel affiche l'ordre lâché, puis celui de la base). Pendant le glissement, une liste
    // reçue (synchronisation) ne fait pas revenir la ligne sous le doigt.
    var dragOrder by remember { mutableStateOf<List<Todo>?>(null) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.todos) { if (draggedId == null) dragOrder = null }
    val shown = dragOrder ?: state.todos
    val haptics = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val current = dragOrder ?: state.todos
        // Une tâche reste dans son groupe : à faire ou terminées.
        if (current.getOrNull(from.index)?.isDone == current.getOrNull(to.index)?.isDone) {
            dragOrder = current.toMutableList().apply { add(to.index, removeAt(from.index)) }
        }
    }
    val canReorder = state.filter.allowsReordering && shown.size > 1
    fun drop() {
        val movedId = draggedId ?: return
        draggedId = null
        val reordered = dragOrder ?: return
        // Lâchée à sa place : rien à enregistrer.
        if (reordered == state.todos) dragOrder = null else onDrop(reordered, movedId)
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("Mes tâches", style = MaterialTheme.typography.headlineSmall)
            // Défile à l'horizontale sur les petits écrans plutôt que d'écraser les libellés.
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenCalendar) { Text("Calendrier") }
                TextButton(onClick = onOpenStats) { Text("Statistiques") }
                TextButton(onClick = onOpenTrash) { Text("Corbeille") }
                onOpenComposeMultiplatform?.let { TextButton(onClick = it) { Text("Version Compose MP") } }
            }
            state.syncBanner?.let { banner ->
                StatusBanner(banner.message, actionLabel = "Réessayer".takeIf { banner.canRetry }, onAction = onRetrySync)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChange,
                    placeholder = { Text("Nouvelle tâche") },
                    isError = state.error != null,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onAdd) { Text("Ajouter") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (state.showRemindersDisabledHint) {
                Text(
                    "Activez les notifications dans Réglages pour recevoir les rappels",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state.allTodos.isNotEmpty()) TodoFilterBar(state.query, state.filter, filterActions)

            when {
                state.allTodos.isEmpty() -> Text("Aucune tâche pour le moment", style = MaterialTheme.typography.bodyLarge)
                state.todos.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Aucune tâche ne correspond",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TextButton(onClick = filterActions.onClear) { Text("Tout afficher") }
                }
            }
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(shown, key = { it.id }) { todo ->
                    ReorderableItem(reorderState, key = todo.id, enabled = canReorder) { isDragging ->
                        TodoRow(
                            todo = todo,
                            highlighted = todo.id == state.focusedTodoId || isDragging,
                            onToggle = { onToggle(todo.id) },
                            onDelete = { onDelete(todo.id) },
                            onOpen = { onOpen(todo.id) },
                            onPickReminder = { onPickReminder(todo) },
                            onRemoveReminder = { onRemoveReminder(todo.id) },
                            // Lecteur d'écran : monter ou descendre d'un cran, dans le groupe de la tâche.
                            moveActions = if (canReorder) moveActions(shown, todo, onDrop) else emptyList(),
                            dragHandle = if (canReorder) {
                                Modifier.draggableHandle(
                                    onDragStarted = {
                                        draggedId = todo.id
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = { drop() },
                                )
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Champ de recherche (titre et note) et filtres cumulables, défilant à l'horizontale sur les petits écrans. */
@Composable
private fun TodoFilterBar(query: String, filter: TodoFilter, actions: TodoFilterActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OutlinedTextField(
            value = query,
            onValueChange = actions.onQueryChange,
            placeholder = { Text("Rechercher dans les tâches et les notes") },
            singleLine = true,
            // Recherche dans des notes parfois personnelles : pas d'autocorrection ni de suggestions apprises.
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            trailingIcon = if (query.isNotEmpty()) {
                { TextButton(onClick = { actions.onQueryChange("") }) { Text("Effacer") } }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            STATUS_LABELS.forEach { (status, label) ->
                FilterChip(
                    selected = filter.status == status,
                    onClick = { actions.onStatusChange(status) },
                    label = { Text(label) },
                )
            }
            FilterChip(selected = filter.withReminder, onClick = actions.onToggleReminder, label = { Text("Avec rappel") })
            FilterChip(
                selected = filter.withAttachments,
                onClick = actions.onToggleAttachments,
                label = { Text("Avec pièce jointe") },
            )
            // Tri, pas filtre : il désactive le glisser-déposer tant qu'il est choisi.
            FilterChip(selected = filter.byPriority, onClick = actions.onTogglePrioritySort, label = { Text("Par priorité") })
        }
    }
}

private val STATUS_LABELS = listOf(
    TodoStatusFilter.All to "Toutes",
    TodoStatusFilter.Pending to "À faire",
    TodoStatusFilter.Done to "Terminées",
)

@Composable
private fun TodoRow(
    todo: Todo,
    highlighted: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onPickReminder: () -> Unit,
    onRemoveReminder: () -> Unit,
    moveActions: List<CustomAccessibilityAction>,
    dragHandle: Modifier?,
) {
    val background = if (highlighted) MvpColors.highlight else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(Radius.md))
            .semantics { if (moveActions.isNotEmpty()) customActions = moveActions },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Poignée du glisser-déposer, absente quand la liste est triée par priorité. Masquée aux lecteurs d'écran,
        // qui ont « Monter » et « Descendre ».
        dragHandle?.let {
            Text(
                "≡",
                style = MaterialTheme.typography.titleLarge,
                modifier = it.padding(horizontal = Spacing.sm).clearAndSetSemantics {},
            )
        }
        Checkbox(checked = todo.isDone, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f).clickable(onClickLabel = "Ouvrir la fiche", onClick = onOpen)) {
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
            )
            priorityBadge(todo.priority)?.let {
                Text(it, color = priorityColor(todo.priority), style = MaterialTheme.typography.bodySmall)
            }
            todo.dueDate?.let { dueDate ->
                val overdue = todo.isOverdueToday()
                Text(
                    "Échéance le ${formatDueDate(dueDate)}" + if (overdue) " · en retard" else "",
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            todo.locationReminder?.let { Text("En arrivant : ${it.label}", style = MaterialTheme.typography.bodySmall) }
            detailsSummary(todo)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            todo.reminderAt?.let { at ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // La date passe à la ligne si besoin : « Retirer » garde sa largeur au lieu d'être écrasé
                    Text(
                        "Rappel le ${formatReminder(at)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TextButton(onClick = onRemoveReminder) { Text("Retirer", maxLines = 1) }
                }
            }
        }
        TextButton(onClick = onPickReminder, enabled = !todo.isDone) { Text("Rappel") }
        TextButton(onClick = onDelete) { Text("Supprimer") }
    }
}

/**
 * Actions « Monter » et « Descendre » d'une tâche pour les lecteurs d'écran : échange avec sa voisine si elle est du
 * même groupe (à faire, terminées), comme un glisser-déposer d'un cran.
 */
private fun moveActions(
    shown: List<Todo>,
    todo: Todo,
    onDrop: (List<Todo>, String) -> Unit,
): List<CustomAccessibilityAction> {
    val index = shown.indexOf(todo)
    return listOfNotNull(
        moveAction("Monter", shown, index, index - 1, onDrop),
        moveAction("Descendre", shown, index, index + 1, onDrop),
    )
}

private fun moveAction(
    label: String,
    shown: List<Todo>,
    from: Int,
    to: Int,
    onDrop: (List<Todo>, String) -> Unit,
): CustomAccessibilityAction? {
    val moved = shown.getOrNull(from) ?: return null
    if (shown.getOrNull(to)?.isDone != moved.isDone) return null
    return CustomAccessibilityAction(label) {
        onDrop(shown.toMutableList().apply { add(to, removeAt(from)) }, moved.id)
        true
    }
}

/** « Note · 2 pièces jointes » sous le titre ; `null` sans note ni pièce jointe. */
private fun detailsSummary(todo: Todo): String? = listOfNotNull(
    "Note".takeIf { todo.note.isNotEmpty() },
    when (todo.attachmentCount) {
        0 -> null
        1 -> "1 pièce jointe"
        else -> "${todo.attachmentCount} pièces jointes"
    },
).joinToString(" · ").ifEmpty { null }

/** Durée de la mise en évidence d'une tâche ouverte par un lien. */
private const val FOCUS_DURATION_MILLIS = 3_000L

private fun formatReminder(at: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

/** Sélecteurs système : la date, puis l'heure. Part du rappel existant, sinon de l'heure suivante. */
private fun pickReminder(context: Context, current: Long?, onPicked: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val initial = current?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
        ?: LocalDateTime.now(zone).plusHours(1).withMinute(0)
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val picked = LocalDateTime.of(year, month + 1, day, hour, minute)
                    onPicked(picked.atZone(zone).toInstant().toEpochMilli())
                },
                initial.hour,
                initial.minute,
                DateFormat.is24HourFormat(context),
            ).show()
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).apply { datePicker.minDate = System.currentTimeMillis() - 1_000 }.show()
}
