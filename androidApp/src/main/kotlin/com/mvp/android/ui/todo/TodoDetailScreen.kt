package com.mvp.android.ui.todo

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mvp.android.ui.common.RecentsPrivacy
import com.mvp.android.ui.theme.Radius
import com.mvp.android.ui.theme.Sizes
import com.mvp.android.ui.theme.Spacing
import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.TodoAttachment
import com.mvp.feature.todo.domain.usecase.AddTodoAttachmentUseCase
import com.mvp.feature.todo.domain.usecase.UpdateTodoNoteUseCase
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.util.Locale

/**
 * Fiche d'une tâche (ADR 0014) : note et pièces jointes. Affichée à la place de la liste, dans l'onglet : elle
 * reste derrière le verrou de l'app, et l'aperçu des apps récentes est masqué. Sélecteurs système sans permission : photos (Photo Picker) et fichiers
 * (PDF, texte, images).
 */
@Composable
fun TodoDetailScreen(todoId: String, onClose: () -> Unit, viewModel: TodoDetailViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(todoId) { viewModel.open(todoId) }
    // Photos et documents personnels : jamais dans l'aperçu des apps récentes, verrou activé ou non.
    RecentsPrivacy.HideWhileShown()
    val close = {
        viewModel.close()
        onClose()
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onFilePicked)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onFilePicked)
    }

    val previewed = state.previewed
    BackHandler { if (previewed != null) viewModel.onPreview(null) else close() }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when {
                previewed != null -> {
                    Header(previewed.fileName, actionLabel = "Fermer", onAction = { viewModel.onPreview(null) })
                    AttachmentPreview(previewed, Modifier.weight(1f))
                }
                state.isMissing -> {
                    Header("", actionLabel = "Retour", onAction = close)
                    Text("Cette tâche a été supprimée", style = MaterialTheme.typography.bodyLarge)
                }
                else -> {
                    Header(state.todo?.title.orEmpty(), actionLabel = "Retour", onAction = close)
                    DetailContent(
                        state = state,
                        onNoteChange = viewModel::onNoteChange,
                        onSaveNote = { viewModel.onSaveNote() },
                        onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onPickFile = { pickFile.launch(PICKABLE_TYPES) },
                        onOpen = viewModel::onPreview,
                        onDelete = { viewModel.onDeleteAttachment(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    state: TodoDetailUiState,
    onNoteChange: (String) -> Unit,
    onSaveNote: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onOpen: (TodoAttachment) -> Unit,
    onDelete: (String) -> Unit,
) {
    OutlinedTextField(
        value = state.noteDraft,
        onValueChange = onNoteChange,
        label = { Text("Note") },
        minLines = 3,
        maxLines = 8,
        supportingText = { Text("${state.noteDraft.trim().length} / ${UpdateTodoNoteUseCase.MAX_NOTE_LENGTH}") },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onSaveNote, enabled = state.noteChanged) { Text("Enregistrer la note") }

    Text(
        "Pièces jointes (${state.attachments.size} / ${AddTodoAttachmentUseCase.MAX_ATTACHMENTS_PER_TODO})",
        style = MaterialTheme.typography.titleMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(onClick = onPickPhoto, enabled = state.canAddAttachment) { Text("Ajouter une photo") }
        OutlinedButton(onClick = onPickFile, enabled = state.canAddAttachment) { Text("Ajouter un fichier") }
    }
    if (state.isAdding) Text("Ajout en cours…", style = MaterialTheme.typography.bodySmall)
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Text(
        "Photos (sans leur position), PDF et fichiers texte, 10 Mo au plus. Ils restent sur cet appareil.",
        style = MaterialTheme.typography.bodySmall,
    )

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items(state.attachments, key = { it.id }) { attachment ->
            AttachmentRow(attachment = attachment, onOpen = { onOpen(attachment) }, onDelete = { onDelete(attachment.id) })
        }
    }
}

@Composable
private fun Header(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onAction) { Text(actionLabel) }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AttachmentRow(attachment: TodoAttachment, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.thumbnail)
                .clip(RoundedCornerShape(Radius.sm))
                .background(MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center,
        ) {
            if (attachment.type == AttachmentType.Photo) {
                AsyncImage(
                    model = File(attachment.localPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    if (attachment.type == AttachmentType.Pdf) "PDF" else "TXT",
                    color = MaterialTheme.colorScheme.onSecondary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(attachment.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatSize(attachment.sizeBytes), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onDelete) { Text("Retirer", maxLines = 1) }
    }
}

/** Taille lisible : « 850 Ko », « 2,4 Mo ». */
private fun formatSize(bytes: Long): String =
    if (bytes < 1024 * 1024) {
        "${(bytes + 1023) / 1024} Ko"
    } else {
        String.format(Locale.FRANCE, "%.1f Mo", bytes / (1024.0 * 1024.0))
    }

/** Sélecteur de fichiers : les images y sont aussi réenregistrées en JPEG. */
private val PICKABLE_TYPES = arrayOf(AttachmentType.Pdf.mimeType, AttachmentType.Text.mimeType, "image/*")
