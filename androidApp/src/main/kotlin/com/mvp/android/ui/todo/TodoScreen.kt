package com.mvp.android.ui.todo

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
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvp.android.ui.theme.Spacing
import com.mvp.feature.todo.domain.model.Todo
import org.koin.androidx.compose.koinViewModel

@Composable
fun TodoScreen(viewModel: TodoViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodoContent(
        state = state,
        onInputChange = viewModel::onInputChange,
        onAdd = { viewModel.onAdd() },
        onToggle = { viewModel.onToggle(it) },
        onDelete = { viewModel.onDelete(it) },
    )
}

@Composable
private fun TodoContent(
    state: TodoUiState,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
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

            if (state.todos.isEmpty()) {
                Text("Aucune tâche pour le moment", style = MaterialTheme.typography.bodyLarge)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
        Text(
            text = todo.title,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDelete) { Text("Supprimer") }
    }
}
