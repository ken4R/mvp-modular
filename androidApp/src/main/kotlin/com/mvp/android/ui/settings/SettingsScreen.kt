package com.mvp.android.ui.settings

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvp.android.ui.applock.rememberDeviceAuthentication
import com.mvp.android.ui.theme.Spacing
import com.mvp.core.analytics.domain.model.AnalyticsJournal
import com.mvp.core.analytics.domain.model.TrackedEvent
import com.mvp.feature.settings.domain.model.Consent
import com.mvp.feature.settings.domain.model.ConsentTexts
import com.mvp.feature.settings.domain.model.ConsentType
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
    }
    // Le journal de mesure n'existe que dans les builds de debug.
    val isDebugBuild = remember { context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }

    // Android 13+ : les notifications demandent une permission à l'exécution.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onNotificationsChange(true) else viewModel.onNotificationPermissionDenied()
    }
    val onNotificationsChange: (Boolean) -> Unit = { enabled ->
        val needsPermission = enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            needsPermission -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            enabled && !NotificationManagerCompat.from(context).areNotificationsEnabled() -> viewModel.onNotificationPermissionDenied()
            else -> viewModel.onNotificationsChange(enabled)
        }
    }

    // Changer le verrou demande d'abord une authentification réussie (activer : on vérifie aussi qu'elle marche).
    val authenticateToEnable = rememberDeviceAuthentication(title = "Activer le déverrouillage biométrique") {
        viewModel.onAppLockAuthResult(enabled = true, result = it)
    }
    val authenticateToDisable = rememberDeviceAuthentication(title = "Couper le déverrouillage biométrique") {
        viewModel.onAppLockAuthResult(enabled = false, result = it)
    }
    val onAppLockChange: (Boolean) -> Unit = { enabled -> if (enabled) authenticateToEnable() else authenticateToDisable() }

    // L'utilisateur peut couper les notifications dans les paramètres système pendant que l'app est en arrière-plan.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onSystemNotificationsChecked(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    SettingsContent(
        state = state,
        appVersion = appVersion,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onSaveProfile = { viewModel.onSaveProfile() },
        onNotificationsChange = onNotificationsChange,
        onAppLockChange = onAppLockChange,
        onConsentChange = { type, granted -> viewModel.onConsentChange(type, granted) },
        onLogout = viewModel::onLogout,
        showAnalyticsJournal = isDebugBuild,
        onSendTestEvent = { viewModel.onSendTestEvent() },
        onFlushAnalytics = { viewModel.onFlushAnalytics() },
    )
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    appVersion: String,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onConsentChange: (ConsentType, Boolean) -> Unit,
    onLogout: () -> Unit,
    showAnalyticsJournal: Boolean,
    onSendTestEvent: () -> Unit,
    onFlushAnalytics: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("Réglages", style = MaterialTheme.typography.headlineSmall)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            SectionTitle("Profil")
            OutlinedTextField(
                value = state.nameInput,
                onValueChange = onNameChange,
                label = { Text("Nom") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.emailInput,
                onValueChange = onEmailChange,
                label = { Text("E-mail (facultatif)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = onSaveProfile) { Text("Enregistrer") }
                state.profileMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }

            HorizontalDivider()
            SectionTitle("Notifications")
            SwitchRow(
                title = "Recevoir des notifications",
                subtitle = null,
                checked = state.notificationsEnabled,
                onCheckedChange = onNotificationsChange,
            )

            HorizontalDivider()
            SectionTitle("Sécurité")
            SwitchRow(
                title = "Déverrouillage biométrique",
                subtitle = "Empreinte, visage ou code de l'appareil à l'ouverture et au retour dans l'app",
                checked = state.appLockEnabled,
                onCheckedChange = onAppLockChange,
            )

            HorizontalDivider()
            SectionTitle("Consentements")
            state.consents.forEach { consent ->
                val text = ConsentTexts.of(consent.type)
                SwitchRow(
                    title = text.title,
                    subtitle = "${text.summary}\n${consent.decisionLabel()}",
                    checked = consent.granted,
                    onCheckedChange = { onConsentChange(consent.type, it) },
                )
            }

            if (showAnalyticsJournal) {
                HorizontalDivider()
                AnalyticsJournalSection(state.analyticsJournal, onSendTestEvent, onFlushAnalytics)
            }

            HorizontalDivider()
            SectionTitle("Compte")
            state.accountEmail?.let { Text("Connecté avec $it", style = MaterialTheme.typography.bodyLarge) }
            Text(
                "La déconnexion efface les tâches, leurs rappels, le profil et les réglages de cet appareil.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onLogout, enabled = !state.isLoggingOut) { Text("Se déconnecter") }

            HorizontalDivider()
            SectionTitle("À propos")
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Version", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(appVersion, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Date du dernier choix, ou rien de choisi : le consentement est alors refusé. */
private fun Consent.decisionLabel(): String {
    val date = decidedAt ?: return "Pas encore choisi : refusé par défaut"
    val formatted = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(date))
    return "${if (granted) "Accepté" else "Refusé"} le $formatted (texte v$textVersion)"
}

/**
 * Journal de mesure (builds de debug) : ce qui attend sur l'appareil et ce qui a été « envoyé ».
 * Pas encore d'outil de mesure : les envois restent en mémoire sur l'appareil.
 */
@Composable
private fun AnalyticsJournalSection(journal: AnalyticsJournal, onSendTestEvent: () -> Unit, onFlush: () -> Unit) {
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }
    SectionTitle("Journal de mesure (debug)")
    Text(
        "Rien n'est gardé sans le consentement Mesure d'audience. Envoyé par lots, ou quand l'app passe en arrière-plan.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(onClick = onSendTestEvent) { Text("Événement test") }
        Button(onClick = onFlush, enabled = journal.pending.isNotEmpty()) { Text("Envoyer maintenant") }
    }
    Text("En attente : ${journal.pending.size}", style = MaterialTheme.typography.bodyLarge)
    journal.pending.forEach { Text(it.describe(timeFormat), style = MaterialTheme.typography.bodySmall) }
    Text("Envoyés : ${journal.sent.size}", style = MaterialTheme.typography.bodyLarge)
    journal.sent.forEach { Text(it.describe(timeFormat), style = MaterialTheme.typography.bodySmall) }
}

private fun TrackedEvent.describe(timeFormat: DateFormat): String =
    buildString {
        append(timeFormat.format(Date(timestamp)))
        append("  ")
        append(name)
        properties.forEach { (key, value) -> append("  $key=$value") }
    }
