package com.mvp.android.ui.featureflags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mvp.android.ui.theme.Spacing
import com.mvp.core.featureflags.domain.model.FeatureFlagState
import com.mvp.core.featureflags.domain.model.FeatureStage
import com.mvp.core.featureflags.domain.model.FlagOrigin

/**
 * Menu « Feature flags (debug) » de Réglages : chaque flag avec son étape, sa valeur et son origine. Un interrupteur
 * pose un réglage local (gardé sur l'appareil, sans effet en release).
 */
@Composable
fun FeatureFlagsSection(
    state: FeatureFlagsUiState,
    onOverride: (FeatureFlagState, Boolean) -> Unit,
    onReset: () -> Unit,
    onRefresh: () -> Unit,
) {
    Text("Feature flags (debug)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Text(
        "Priorité : réglage local, puis valeur distante, puis valeur par défaut. Une feature en développement n'est jamais visible en release.",
        style = MaterialTheme.typography.bodySmall,
    )
    state.flags.forEach { flagState ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(flagState.flag.label, style = MaterialTheme.typography.bodyLarge)
                Text("${flagState.flag.key} · ${flagState.flag.stage.label()} · ${flagState.origin.label()}", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = flagState.enabled, onCheckedChange = { onOverride(flagState, it) })
        }
    }
    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(onClick = onReset) { Text("Réinitialiser") }
        OutlinedButton(onClick = onRefresh, enabled = !state.refreshing) { Text("Rafraîchir le distant") }
    }
}

/**
 * Feature en développement (flag `preview_banner`) : n'apparaît qu'en debug, une fois allumée dans le menu.
 * Appelée derrière `BuildConfig.DEV_FLAGS` : R8 retire ce texte du build release (vérifié par la CI).
 */
@Composable
fun PreviewBannerCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Bientôt", style = MaterialTheme.typography.titleMedium)
            Text("Une nouvelle fonction en préparation, visible seulement dans les builds de debug.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun FeatureStage.label(): String = when (this) {
    FeatureStage.InDevelopment -> "en développement"
    FeatureStage.Released -> "livrée"
}

private fun FlagOrigin.label(): String = when (this) {
    FlagOrigin.Default -> "défaut"
    FlagOrigin.Remote -> "distant"
    FlagOrigin.Override -> "local"
    FlagOrigin.ReleaseLocked -> "coupée en release"
}
