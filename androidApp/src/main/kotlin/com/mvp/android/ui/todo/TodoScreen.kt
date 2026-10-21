package com.mvp.android.ui.todo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvp.android.ui.theme.Spacing
import com.mvp.feature.todo.domain.model.Todo
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TodoScreen(viewModel: TodoViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    TodoContent(
        state = state,
        onInputChange = viewModel::onInputChange,
        onAdd = { viewModel.onAdd() },
        onToggle = { viewModel.onToggle(it) },
        onDelete = { viewModel.onDelete(it) },
        onPickReminder = { todo -> pickReminder(context, todo.reminderAt) { at -> viewModel.onSetReminder(todo.id, at) } },
        onRemoveReminder = { viewModel.onSetReminder(it, null) },
    )
}

@Composable
private fun TodoContent(
    state: TodoUiState,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPickReminder: (Todo) -> Unit,
    onRemoveReminder: (String) -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("Mes tâches", style = MaterialTheme.typography.headlineSmall)

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

            if (state.todos.isEmpty()) {
                Text("Aucune tâche pour le moment", style = MaterialTheme.typography.bodyLarge)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(state.todos, key = { it.id }) { todo ->
                    TodoRow(
                        todo = todo,
                        onToggle = { onToggle(todo.id) },
                        onDelete = { onDelete(todo.id) },
                        onPickReminder = { onPickReminder(todo) },
                        onRemoveReminder = { onRemoveReminder(todo.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onPickReminder: () -> Unit,
    onRemoveReminder: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = todo.isDone, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
            )
            todo.reminderAt?.let { at ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rappel le ${formatReminder(at)}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRemoveReminder) { Text("Retirer") }
                }
            }
        }
        TextButton(onClick = onPickReminder, enabled = !todo.isDone) { Text("Rappel") }
        TextButton(onClick = onDelete) { Text("Supprimer") }
    }
}

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
