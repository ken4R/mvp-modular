package com.mvp.android.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.mvp.android.ui.theme.Radius
import com.mvp.android.ui.theme.Spacing

/**
 * Bandeau d'état du réseau ou de la synchronisation (mode hors ligne, ADR 0012), avec une action facultative.
 * Annoncé par TalkBack quand son texte change.
 */
@Composable
fun StatusBanner(message: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Surface(
        color = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        ) {
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(vertical = Spacing.xs))
            actionLabel?.let {
                TextButton(onClick = onAction) {
                    Text(it, color = MaterialTheme.colorScheme.onSecondary)
                }
            }
        }
    }
}
