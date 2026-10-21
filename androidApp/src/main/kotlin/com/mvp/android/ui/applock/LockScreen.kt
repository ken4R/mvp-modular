package com.mvp.android.ui.applock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.mvp.android.ui.theme.Spacing

/** Remplace tout le contenu de l'app tant qu'elle est verrouillée. Le prompt système s'ouvre tout seul. */
@Composable
fun LockScreen(state: AppLockUiState, onAuthResult: (DeviceAuthResult) -> Unit, onLogout: () -> Unit) {
    val authenticate = rememberDeviceAuthentication(title = "Déverrouiller MVP Modular", onResult = onAuthResult)
    // Une seule ouverture automatique par verrouillage : après une annulation, c'est le bouton.
    LaunchedEffect(Unit) { authenticate() }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("App verrouillée", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Utilisez votre empreinte, votre visage ou le code de l'appareil.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            Button(onClick = authenticate) { Text("Déverrouiller") }
            TextButton(onClick = onLogout, enabled = !state.isLoggingOut) { Text("Se déconnecter") }
        }
    }
}
