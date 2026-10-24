package com.mvp.android.ui.todo

import android.content.Intent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mvp.android.ui.common.RecentsPrivacy
import com.mvp.android.ui.theme.Radius
import com.mvp.android.ui.theme.Sizes
import com.mvp.android.ui.theme.Spacing
import com.mvp.feature.todo.domain.model.AttachmentType
import com.mvp.feature.todo.domain.model.TodoAttachment
import com.mvp.feature.todo.domain.model.TodoShare
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
    // Le brouillon de la note survit à un changement d'onglet, au verrou et à l'arrêt de l'app en arrière-plan.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveChangedNote() }
    DisposableEffect(todoId) { onDispose { viewModel.saveChangedNote() } }
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

    // Feuille de partage du système (ADR 0017) : du texte seulement, jamais de fichier joint.
    val context = LocalContext.current
    LaunchedEffect(state.share) {
        val share = state.share ?: return@LaunchedEffect
        context.startActivity(shareIntent(share))
        viewModel.onShareShown()
    }
    if (state.askIncludeNote) {
        IncludeNoteDialog(onConfirm = { viewModel.onShareConfirmed(it) }, onDismiss = viewModel::onShareCancelled)
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
                    Header(
                        state.todo?.title.orEmpty(),
                        actionLabel = "Retour",
                        onAction = close,
                        onShare = viewModel::onShare.takeIf { state.todo != null },
                    )
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
private fun Header(title: String, actionLabel: String, onAction: () -> Unit, onShare: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onAction) { Text(actionLabel) }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        onShare?.let { TextButton(onClick = it) { Text("Partager", maxLines = 1) } }
    }
}

/** La note peut être personnelle : elle n'est ajoutée au partage que cochée. Les pièces jointes, jamais. */
@Composable
private fun IncludeNoteDialog(onConfirm: (includeNote: Boolean) -> Unit, onDismiss: () -> Unit) {
    var includeNote by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partager la tâche") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { includeNote = !includeNote },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includeNote, onCheckedChange = { includeNote = it })
                    Text("Inclure la note", style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    "Le titre, l'état et un lien vers la tâche sont partagés. Les pièces jointes restent sur cet appareil.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(includeNote) }) { Text("Partager") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * `ACTION_SEND` en texte brut, dans le sélecteur du système (aucune app choisie par défaut). L'app choisie s'ouvre
 * dans sa propre tâche : son écran (un mail avec la note…) n'apparaît pas dans l'aperçu récent de l'app, et le
 * retour à l'app repasse par le verrou.
 */
private fun shareIntent(share: TodoShare): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        putExtra(Intent.EXTRA_SUBJECT, share.subject)
        putExtra(Intent.EXTRA_TEXT, share.text)
    }
    return Intent.createChooser(send, "Partager la tâche")
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
