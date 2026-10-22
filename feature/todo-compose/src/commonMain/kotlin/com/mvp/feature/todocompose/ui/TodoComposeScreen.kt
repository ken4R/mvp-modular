package com.mvp.feature.todocompose.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mvp.feature.todo.domain.model.Todo
import com.mvp.feature.todocompose.ui.theme.SharedMvpTheme
import com.mvp.feature.todocompose.ui.theme.SharedSpacing
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Écran Tâches écrit une seule fois en Compose Multiplatform : affiché tel quel par l'app Android
 * et, sur iOS, dans un UIViewController (voir iosMain). Mêmes use cases que les écrans natifs,
 * donc mêmes données. Reprend la liste, l'ajout, la case à cocher et la suppression ; le choix
 * d'un rappel et les liens profonds restent dans les écrans natifs (ADR 0010).
 *
 * [onClose] : affiche un bouton « Fermer » (l'écran est ouvert depuis l'écran natif).
 */
@Composable
fun TodoComposeScreen(
    onClose: (() -> Unit)? = null,
    viewModel: TodoComposeViewModel = viewModel { createTodoComposeViewModel() },
) {
    val state by viewModel.state.collectAsState()
    SharedMvpTheme {
        TodoComposeContent(
            state = state,
            onClose = onClose,
            onInputChange = viewModel::onInputChange,
            onAdd = { viewModel.onAdd() },
            onToggle = { viewModel.onToggle(it) },
            onDelete = { viewModel.onDelete(it) },
        )
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun TodoComposeContent(
    state: TodoComposeUiState,
    onClose: (() -> Unit)?,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(SharedSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SharedSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mes tâches", style = MaterialTheme.typography.headlineSmall)
                    Text("Écran partagé en Compose Multiplatform", style = MaterialTheme.typography.bodySmall)
                }
                onClose?.let { TextButton(onClick = it) { Text("Fermer") } }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedSpacing.sm)) {
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
            if (state.showRemindersDisabledHint(Clock.System.now().toEpochMilliseconds())) {
                Text(
                    "Activez les notifications dans Réglages pour recevoir les rappels",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state.todos.isEmpty()) {
                Text("Aucune tâche pour le moment", style = MaterialTheme.typography.bodyLarge)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(SharedSpacing.xs)) {
                items(state.todos, key = { it.id }) { todo ->
                    TodoRow(todo = todo, onToggle = { onToggle(todo.id) }, onDelete = { onDelete(todo.id) })
                }
            }
        }
    }
}

@Composable
private fun TodoRow(todo: Todo, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = todo.isDone, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
            )
            todo.reminderAt?.let { at ->
                Text("Rappel le ${formatReminder(at)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = onDelete) { Text("Supprimer") }
    }
}
