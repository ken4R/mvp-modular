package com.mvp.android.ui.todo

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import com.mvp.feature.todo.domain.model.LocationReminder
import com.mvp.feature.todo.domain.model.TodoAttachment
import com.mvp.feature.todo.domain.model.TodoCalendarEvent
import com.mvp.feature.todo.domain.model.TodoShare
import com.mvp.feature.todo.domain.model.TodoPriority
import com.mvp.feature.todo.domain.usecase.AddTodoAttachmentUseCase
import com.mvp.feature.todo.domain.usecase.UpdateTodoNoteUseCase
import java.io.File
import java.time.ZoneId
import java.util.Locale
import org.koin.androidx.compose.koinViewModel

/**
 * Fiche d'une tâche (ADR 0014) : échéance et export vers le calendrier (ADR 0022), note et pièces jointes. Affichée à la place de la liste, dans l'onglet : elle
 * reste derrière le verrou de l'app, et l'aperçu des apps récentes est masqué. Sélecteurs système sans permission : photos (Photo Picker) et fichiers
 * (PDF, texte, images).
 */
@Composable
fun TodoDetailScreen(
    todoId: String,
    onClose: () -> Unit,
    onStartFocusMode: ((todoId: String, title: String) -> Unit)? = null,
    viewModel: TodoDetailViewModel = koinViewModel(),
) {
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
    // Écran d'ajout du calendrier du téléphone (ADR 0022) : rien n'est écrit sans la confirmation de l'utilisateur.
    LaunchedEffect(state.calendarEvent) {
        val event = state.calendarEvent ?: return@LaunchedEffect
        try {
            context.startActivity(calendarIntent(event))
            viewModel.onCalendarEventShown()
        } catch (e: ActivityNotFoundException) {
            viewModel.onCalendarUnavailable()
        }
    }
    if (state.askIncludeNote) {
        IncludeNoteDialog(onConfirm = { viewModel.onShareConfirmed(it) }, onDismiss = viewModel::onShareCancelled)
    }
    val place = rememberPlacePermissions(viewModel)

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
                        // Mode focus (ADR 0028) : une tâche à faire seulement.
                        onStartFocusMode = state.todo?.takeIf { !it.isDone }?.let { todo ->
                            onStartFocusMode?.let { start -> { start(todo.id, todo.title) } }
                        },
                        onPickDueDate = { pickDueDate(context, state.todo?.dueDate) { viewModel.onSetDueDate(it) } },
                        onRemoveDueDate = { viewModel.onSetDueDate(null) },
                        onAddToCalendar = { viewModel.onAddToCalendar() },
                        onSetPriority = { viewModel.onSetPriority(it) },
                        place = place,
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
    onStartFocusMode: (() -> Unit)?,
    onPickDueDate: () -> Unit,
    onRemoveDueDate: () -> Unit,
    onAddToCalendar: () -> Unit,
    onSetPriority: (TodoPriority) -> Unit,
    place: PlaceActions,
    onNoteChange: (String) -> Unit,
    onSaveNote: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onOpen: (TodoAttachment) -> Unit,
    onDelete: (String) -> Unit,
) {
    onStartFocusMode?.let { OutlinedButton(onClick = it) { Text("Mode focus (Pomodoro)") } }
    PrioritySection(state.todo?.priority ?: TodoPriority.None, onSetPriority)
    DueDateSection(state.todo?.dueDate, state.todo?.isOverdueToday() == true, onPickDueDate, onRemoveDueDate, onAddToCalendar)
    LocationReminderSection(state, place)

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

/** Priorité de la tâche (ADR 0024), synchronisée comme le titre. */
@Composable
private fun PrioritySection(priority: TodoPriority, onSelect: (TodoPriority) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Priorité", style = MaterialTheme.typography.titleMedium)
        // Défile à l'horizontale sur les petits écrans plutôt que d'écraser les libellés.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PRIORITY_LABELS.forEach { (value, label) ->
                FilterChip(selected = priority == value, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

/** Échéance de la tâche : la choisir, la retirer, ou l'ajouter au calendrier du téléphone. */
@Composable
private fun DueDateSection(
    dueDate: Int?,
    overdue: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    onAddToCalendar: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Échéance", style = MaterialTheme.typography.titleMedium)
        if (dueDate == null) {
            OutlinedButton(onClick = onPick) { Text("Choisir une date") }
        } else {
            Text(formatLongDueDate(dueDate), style = MaterialTheme.typography.bodyLarge)
            if (overdue) {
                Text("En retard", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onPick) { Text("Modifier", maxLines = 1) }
                TextButton(onClick = onRemove) { Text("Retirer", maxLines = 1) }
            }
            OutlinedButton(onClick = onAddToCalendar) { Text("Ajouter au calendrier") }
            Text(
                "Le titre et la date seulement, jamais la note. L'événement reste dans le calendrier même après la déconnexion.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Actions de la section « Rappel par lieu », permissions comprises. */
private class PlaceActions(
    val onLabelChange: (String) -> Unit,
    val onRadiusChange: (Int) -> Unit,
    val onPlaceHere: () -> Unit,
    val onAllow: () -> Unit,
    val onRemove: () -> Unit,
)

/**
 * Permissions des rappels par lieu (ADR 0023), demandées au moment de poser un rappel : d'abord la position exacte,
 * puis, une fois le rappel posé, « Toujours autoriser » (Android 10+), expliqué avant l'écran du système. Sur
 * Android 11+, cette dernière s'accorde dans les paramètres de l'app, que le système ouvre lui-même.
 */
@Composable
private fun rememberPlacePermissions(viewModel: TodoDetailViewModel): PlaceActions {
    val context = LocalContext.current
    var explainBackground by remember { mutableStateOf(false) }
    val requestBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshLocationAvailability()
    }
    // « Toujours autoriser » seulement une fois le rappel posé : pas pour une pose refusée (position introuvable…).
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.placeJustSet) {
        if (!state.placeJustSet) return@LaunchedEffect
        if (!context.hasBackgroundLocation()) explainBackground = true
        viewModel.onPlaceSetHandled()
    }
    val placeHere = { viewModel.onSetPlaceHere() }
    val requestPrecise = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        // L'utilisateur peut n'accorder que la position approximative (Android 12+) : elle ne suffit pas.
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) placeHere() else viewModel.onPrecisePermissionRefused()
        viewModel.refreshLocationAvailability()
    }
    if (explainBackground) {
        BackgroundLocationDialog(
            onConfirm = {
                explainBackground = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) requestBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            },
            onDismiss = { explainBackground = false },
        )
    }
    val precise = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    return PlaceActions(
        onLabelChange = viewModel::onPlaceLabelChange,
        onRadiusChange = viewModel::onPlaceRadiusChange,
        onPlaceHere = { if (context.hasPreciseLocation()) placeHere() else requestPrecise.launch(precise) },
        onAllow = {
            when {
                !context.hasPreciseLocation() -> requestPrecise.launch(precise)
                !context.hasBackgroundLocation() -> explainBackground = true
                // Déjà accordées mais refusées ailleurs (localisation coupée…) : les paramètres de l'app.
                else -> context.startActivity(appSettingsIntent(context))
            }
        },
        onRemove = { viewModel.onRemovePlace() },
    )
}

/** Rappel par lieu (ADR 0023) : à chaque arrivée dans la zone, tant que la tâche est à faire. La position reste sur l'appareil. */
@Composable
private fun LocationReminderSection(state: TodoDetailUiState, actions: PlaceActions) {
    val todo = state.todo
    val place = todo?.locationReminder
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Rappel par lieu", style = MaterialTheme.typography.titleMedium)
        if (place != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "En arrivant à ${place.label} (${place.radiusMeters} m)",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(onClick = actions.onRemove) { Text("Retirer", maxLines = 1) }
            }
            if (!state.locationRemindersAvailable) {
                Text(
                    "Ce rappel ne part pas : autorisez la position exacte et « Toujours autoriser ».",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = actions.onAllow) { Text("Autoriser") }
            }
        } else {
            // Compact dans la fiche : le nom et le rayon se choisissent dans une boîte de dialogue.
            var choosing by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { choosing = true }, enabled = todo != null && !todo.isDone && !state.isLocating) {
                Text(if (state.isLocating) "Recherche de la position…" else "Ajouter un rappel par lieu")
            }
            if (choosing) {
                PlaceDialog(
                    state = state,
                    actions = actions,
                    onConfirm = {
                        choosing = false
                        actions.onPlaceHere()
                    },
                    onDismiss = { choosing = false },
                )
            }
        }
        state.placeError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (!state.remindersEnabled) {
            Text("Activez les notifications dans Réglages pour recevoir les rappels.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Nom et rayon du lieu, puis « Me le rappeler en arrivant ici » : la position actuelle devient le lieu du rappel. */
@Composable
private fun PlaceDialog(state: TodoDetailUiState, actions: PlaceActions, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rappel par lieu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = state.placeLabel,
                    onValueChange = actions.onLabelChange,
                    label = { Text("Nom du lieu (ex. Bureau)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    LocationReminder.RADII.forEach { radius ->
                        FilterChip(
                            selected = state.placeRadius == radius,
                            onClick = { actions.onRadiusChange(radius) },
                            label = { Text("$radius m") },
                        )
                    }
                }
                Text(
                    "Votre position actuelle devient le lieu du rappel. Elle reste sur cet appareil et n'est jamais envoyée. " +
                        "Le rappel part à chaque arrivée tant que la tâche est à faire.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Me le rappeler en arrivant ici") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Expliqué avant l'écran du système : sans « Toujours autoriser », la zone n'est pas surveillée app fermée. */
@Composable
private fun BackgroundLocationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rappels app fermée") },
        text = {
            Text(
                "Pour que le rappel parte quand l'app est fermée, choisissez « Toujours autoriser » pour la position. " +
                    "Elle sert seulement à savoir si vous arrivez au lieu choisi, et reste sur cet appareil.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Continuer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Plus tard") } },
    )
}

private fun Context.hasPreciseLocation() =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Context.hasBackgroundLocation() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
    checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun appSettingsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

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

/**
 * Écran d'ajout d'événement de l'app de calendrier (`ACTION_INSERT`), sans permission : l'utilisateur choisit le
 * compte et confirme. Une journée entière, de minuit à minuit dans le fuseau de l'appareil. Ouvert dans sa propre
 * tâche, comme le partage : il n'apparaît pas dans l'aperçu récent de l'app.
 */
private fun calendarIntent(event: TodoCalendarEvent): Intent {
    val zone = ZoneId.systemDefault()
    val day = event.date.toLocalDate()
    return Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        putExtra(CalendarContract.Events.TITLE, event.title)
        putExtra(CalendarContract.Events.DESCRIPTION, "Ouvrir dans MVP Modular : ${event.link}")
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, day.atStartOfDay(zone).toInstant().toEpochMilli())
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
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
