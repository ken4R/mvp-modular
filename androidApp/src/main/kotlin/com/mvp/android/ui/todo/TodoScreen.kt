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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * [focusRequest] : tâche demandée par un lien, à mettre en évidence ; [onFocusRequestHandled] une fois prise en compte.
 * [onOpenComposeMultiplatform] : affiche un bouton vers la version Compose Multiplatform de l'écran (ADR 0010).
 * Un tap sur une tâche ouvre sa fiche (note et pièces jointes, ADR 0014) à la place de la liste.
 */
@Composable
fun TodoScreen(
    focusRequest: String? = null,
    onFocusRequestHandled: () -> Unit = {},
    onOpenComposeMultiplatform: (() -> Unit)? = null,
    viewModel: TodoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var openedTodoId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(focusRequest) {
        focusRequest?.let {
            // Un lien vers une tâche revient à la liste, où elle est mise en évidence.
            openedTodoId = null
            viewModel.focus(it)
        }
        onFocusRequestHandled()
    }
    openedTodoId?.let { id ->
        TodoDetailScreen(todoId = id, onClose = { openedTodoId = null })
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
        onInputChange = viewModel::onInputChange,
        onAdd = { viewModel.onAdd() },
        onToggle = { viewModel.onToggle(it) },
        onDelete = { viewModel.onDelete(it) },
        onOpen = { openedTodoId = it },
        onPickReminder = { todo -> pickReminder(context, todo.reminderAt) { at -> viewModel.onSetReminder(todo.id, at) } },
        onRemoveReminder = { viewModel.onSetReminder(it, null) },
        onRetrySync = { viewModel.onRetrySync() },
        filterActions = TodoFilterActions(
            onQueryChange = viewModel::onQueryChange,
            onStatusChange = viewModel::onStatusFilterChange,
            onToggleReminder = viewModel::onToggleReminderFilter,
            onToggleAttachments = viewModel::onToggleAttachmentFilter,
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
    val onClear: () -> Unit,
)

@Composable
private fun TodoContent(
    state: TodoUiState,
    listState: LazyListState,
    onOpenComposeMultiplatform: (() -> Unit)?,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
    onPickReminder: (Todo) -> Unit,
    onRemoveReminder: (String) -> Unit,
    onRetrySync: () -> Unit,
    filterActions: TodoFilterActions,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mes tâches", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
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
                items(state.todos, key = { it.id }) { todo ->
                    TodoRow(
                        todo = todo,
                        highlighted = todo.id == state.focusedTodoId,
                        onToggle = { onToggle(todo.id) },
                        onDelete = { onDelete(todo.id) },
                        onOpen = { onOpen(todo.id) },
                        onPickReminder = { onPickReminder(todo) },
                        onRemoveReminder = { onRemoveReminder(todo.id) },
                    )
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
) {
    val background = if (highlighted) MvpColors.highlight else Color.Transparent
    Row(
        modifier = Modifier.fillMaxWidth().background(background, RoundedCornerShape(Radius.md)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = todo.isDone, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f).clickable(onClickLabel = "Ouvrir la fiche", onClick = onOpen)) {
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
            )
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
