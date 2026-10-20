package com.mvp.android.ui.settings

import android.Manifest
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
import com.mvp.android.ui.theme.Spacing
import com.mvp.feature.settings.domain.model.ConsentType
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
    }

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
        onConsentChange = { type, granted -> viewModel.onConsentChange(type, granted) },
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
    onConsentChange: (ConsentType, Boolean) -> Unit,
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
            SectionTitle("Consentements")
            state.consents.forEach { consent ->
                val texts = consent.type.texts()
                SwitchRow(
                    title = texts.first,
                    subtitle = texts.second,
                    checked = consent.granted,
                    onCheckedChange = { onConsentChange(consent.type, it) },
                )
            }

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

/** Titre et explication d'un consentement. */
private fun ConsentType.texts(): Pair<String, String> = when (this) {
    ConsentType.Analytics -> "Mesure d'audience" to "Statistiques anonymes pour améliorer l'app"
    ConsentType.Marketing -> "Communications marketing" to "Offres et nouveautés par e-mail ou notification"
    ConsentType.Personalization -> "Personnalisation" to "Contenus adaptés à votre utilisation"
}
