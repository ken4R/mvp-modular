package com.mvp.android.ui.todo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvp.android.ui.theme.Spacing
import com.mvp.feature.todo.domain.model.TodoTrash
import com.mvp.feature.todo.domain.model.TrashItem
import org.koin.androidx.compose.koinViewModel

/**
 * Corbeille (ADR 0025) : tâches supprimées, restaurables pendant 30 jours. Affichée à la place de la liste, dans
 * l'onglet Tâches (derrière le verrou de l'app). La suppression définitive (une tâche ou toute la corbeille) demande
 * une confirmation.
 */
@Composable
fun TodoTrashScreen(onClose: () -> Unit, viewModel: TodoTrashViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Id de la tâche à supprimer définitivement, ou EMPTY_ALL pour toute la corbeille : confirmation affichée.
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(onBack = onClose)
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Retour") }
                Text(
                    "Corbeille",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (!state.items.isNullOrEmpty()) {
                    TextButton(onClick = { confirm = EMPTY_ALL }) {
                        Text("Vider", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Text(
                "Les tâches supprimées restent ici ${TodoTrash.RETENTION_DAYS} jours, puis sont supprimées définitivement.",
                style = MaterialTheme.typography.bodyMedium,
            )
            val items = state.items
            when {
                state.error -> Text(
                    "Corbeille indisponible",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                items == null -> Unit
                items.isEmpty() -> Text("La corbeille est vide", style = MaterialTheme.typography.bodyLarge)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(items, key = { it.todo.id }) { item ->
                        TrashRow(
                            item = item,
                            onRestore = { viewModel.onRestore(item) },
                            onDelete = { confirm = item.todo.id },
                        )
                    }
                }
            }
        }
    }
    confirm?.let { target ->
        val item = state.items?.firstOrNull { it.todo.id == target }
        val emptyAll = target == EMPTY_ALL
        // La tâche a pu quitter la corbeille entre-temps (restaurée sur un autre appareil) : plus rien à confirmer.
        if (!emptyAll && item == null) return@let
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (emptyAll) "Vider la corbeille ?" else "Supprimer définitivement ?") },
            text = {
                Text(
                    if (emptyAll) {
                        "Toutes les tâches de la corbeille et leurs pièces jointes seront supprimées. C'est irréversible."
                    } else {
                        "« ${item?.todo?.title} » et ses pièces jointes seront supprimées. C'est irréversible."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (emptyAll) viewModel.onEmptyTrash() else item?.let(viewModel::onDeletePermanently)
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun TrashRow(item: TrashItem, onRestore: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            item.todo.title,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.todo.isDone) TextDecoration.LineThrough else null,
        )
        Text(daysLeftText(item.daysLeft), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextButton(onClick = onRestore) { Text("Restaurer") }
            TextButton(onClick = onDelete) { Text("Supprimer définitivement", color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun daysLeftText(days: Int): String =
    if (days <= 1) "Supprimée définitivement dans 1 jour" else "Supprimée définitivement dans $days jours"

/** Valeur de confirmation pour « Vider la corbeille » (jamais un id de tâche, qui est un UUID). */
private const val EMPTY_ALL = "*"
