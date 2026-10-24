package com.mvp.android.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvp.android.ui.applock.isDeviceAuthenticationAvailable
import com.mvp.android.ui.applock.rememberDeviceAuthentication
import com.mvp.android.ui.theme.Spacing
import com.mvp.feature.onboarding.domain.model.OnboardingStep
import com.mvp.feature.onboarding.domain.model.OnboardingTexts
import com.mvp.feature.settings.domain.model.ConsentTexts
import com.mvp.feature.settings.domain.model.ConsentType

/**
 * Présentation du premier lancement (ADR 0016) : une étape par écran, chaque autorisation expliquée avant que
 * le système ne la demande. Toutes les étapes se remettent à plus tard, et « Passer » termine tout de suite.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.loadSteps(isDeviceAuthenticationAvailable(context)) }

    // Android 13+ : permission demandée à l'exécution. Avant, les notifications sont autorisées sauf si
    // l'utilisateur les a coupées dans les paramètres de l'app.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onNotificationsResult(granted)
    }
    // Refusée, la météo reste disponible par la recherche d'une ville : on passe à la suite dans tous les cas.
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onNext() }
    val authenticate = rememberDeviceAuthentication(title = "Activer le déverrouillage biométrique") {
        viewModel.onAppLockAuthResult(it)
    }

    val step = state.step
    if (step == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val onAccept: () -> Unit = {
        when (step) {
            OnboardingStep.Welcome -> viewModel.onNext()
            OnboardingStep.Notifications -> if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onNotificationsResult(NotificationManagerCompat.from(context).areNotificationsEnabled())
            }
            OnboardingStep.Location -> if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.onNext()
            } else {
                locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            OnboardingStep.AppLock -> authenticate()
            OnboardingStep.Analytics -> viewModel.onAnalyticsChoice(true)
        }
    }
    val onDecline: () -> Unit = {
        if (step == OnboardingStep.Analytics) viewModel.onAnalyticsChoice(false) else viewModel.onNext()
    }

    OnboardingContent(
        step = step,
        position = state.index + 1,
        count = state.steps.size,
        enabled = state.enabled,
        error = state.error,
        onAccept = onAccept,
        onDecline = onDecline,
        onSkip = viewModel::onSkip,
    )
}

@Composable
private fun OnboardingContent(
    step: OnboardingStep,
    position: Int,
    count: Int,
    enabled: Boolean,
    error: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onSkip: () -> Unit,
) {
    val text = OnboardingTexts.of(step)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Étape $position sur $count",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSkip, enabled = enabled) { Text(OnboardingTexts.skipLabel) }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(text.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            // L'accord porte sur ce texte précis (versionné), le même que dans Réglages > Consentements.
            if (step == OnboardingStep.Analytics) {
                Text(
                    ConsentTexts.of(ConsentType.Analytics).summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        // Consentement : accepter et refuser ont le même poids visuel (refuser doit être aussi simple qu'accepter).
        if (step == OnboardingStep.Analytics) {
            OutlinedButton(onClick = onAccept, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text.acceptLabel) }
        } else {
            Button(onClick = onAccept, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text.acceptLabel) }
        }
        text.declineLabel?.let { label ->
            OutlinedButton(onClick = onDecline, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
    }
}
