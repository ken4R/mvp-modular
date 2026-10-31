package com.mvp.android.ui.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.mvp.android.ui.common.StatusBanner
import com.mvp.android.ui.theme.Spacing
import com.mvp.feature.focus.domain.model.FocusTimer

/**
 * Mode focus (ADR 0028) : compte à rebours de la phase sur une tâche. Affiché à la place de la liste dans l'onglet
 * Tâches (derrière le verrou de l'app) ; « Réduire » y revient sans arrêter la session, qui continue dans la
 * notification en cours.
 */
@Composable
fun FocusScreen(state: FocusUiState, viewModel: FocusViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    RequestNotificationPermission()
    val timer = state.timer ?: return
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Réduire") }
                Text(
                    "Mode focus",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            Text(timer.state.title, style = MaterialTheme.typography.titleLarge)
            Text(phaseLine(timer), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
            Text(
                timer.remainingLabel,
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    // TalkBack : une annonce par changement d'état, pas à chaque seconde.
                    .clearAndSetSemantics { contentDescription = spokenRemaining(timer) },
            )
            LinearProgressIndicator(progress = { timer.progress }, modifier = Modifier.fillMaxWidth())
            if (timer.state.isFinished) {
                Text(
                    if (timer.state.isBreak) "Pause terminée" else "Concentration terminée, bravo !",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Controls(timer, viewModel)
            state.error?.let { StatusBanner(it) }
        }
    }
}

@Composable
private fun Controls(timer: FocusTimer, viewModel: FocusViewModel) {
    val focus = timer.state
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when {
            focus.isFinished -> Button(onClick = viewModel::next) { Text(nextLabel(timer)) }
            focus.isPaused -> Button(onClick = viewModel::resume) { Text("Reprendre") }
            else -> Button(onClick = viewModel::pause) { Text("Pause") }
        }
        if (!focus.isFinished) OutlinedButton(onClick = viewModel::next) { Text("Passer") }
        TextButton(onClick = viewModel::stop) { Text("Arrêter") }
    }
}

/** Bandeau de la liste des tâches pendant une session, pour revenir à l'écran Focus. */
@Composable
fun FocusBanner(timer: FocusTimer, onOpen: () -> Unit) {
    val status = when {
        timer.state.isFinished -> "terminée"
        timer.state.isPaused -> "en pause · ${timer.remainingLabel}"
        else -> timer.remainingLabel
    }
    StatusBanner("${timer.state.phaseLabel} $status · ${timer.state.title}", actionLabel = "Ouvrir", onAction = onOpen)
}

private fun phaseLine(timer: FocusTimer): String {
    val focus = timer.state
    val done = focus.completedWorkPhases
    val count = if (done == 0) "" else " · $done concentration${if (done > 1) "s" else ""} terminée${if (done > 1) "s" else ""}"
    return focus.phaseLabel + (if (focus.isPaused) " (en pause)" else "") + count
}

private fun nextLabel(timer: FocusTimer): String =
    if (timer.state.nextPhase.isBreak) "Commencer la ${timer.state.nextPhaseLabel.lowercase()}" else "Reprendre la concentration"

private fun spokenRemaining(timer: FocusTimer): String {
    val minutes = ((timer.remainingMillis + 59_999) / 60_000).toInt()
    return when {
        timer.state.isFinished -> "Phase terminée"
        timer.state.isPaused -> "En pause, $minutes minute${if (minutes > 1) "s" else ""} restantes"
        else -> "Environ $minutes minute${if (minutes > 1) "s" else ""} restantes"
    }
}

/** Android 13+ : la notification en cours et l'alerte de fin exigent la permission, demandée à l'ouverture. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
