package com.mvp.android.ui.todo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvp.android.ui.theme.Radius
import com.mvp.android.ui.theme.Sizes
import com.mvp.android.ui.theme.Spacing
import com.mvp.feature.todo.domain.model.CompletionStats
import com.mvp.feature.todo.domain.model.WeekCompletion
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Statistiques (ADR 0019) : tâches terminées par semaine sur les dernières semaines, en barres. Affiché à la place
 * de la liste, dans l'onglet Tâches : il reste derrière le verrou de l'app. Chaque barre est lue par TalkBack
 * (« Semaine du 20 octobre : 3 tâches terminées »).
 */
@Composable
fun TodoStatsScreen(onClose: () -> Unit, viewModel: TodoStatsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Retour") }
                Text(
                    "Statistiques",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            val stats = state.stats
            when {
                stats != null -> StatsContent(stats)
                state.error -> Text(
                    "Statistiques indisponibles",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun StatsContent(stats: CompletionStats) {
    Text("Tâches terminées par semaine", style = MaterialTheme.typography.titleMedium)
    Text(summary(stats), style = MaterialTheme.typography.bodyMedium)
    WeeklyChart(stats)
    if (stats.total == 0) {
        Text(
            "Cochez une tâche pour la voir apparaître ici",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Une barre par semaine, la semaine en cours à droite ; hauteur proportionnelle à la meilleure semaine. */
@Composable
private fun WeeklyChart(stats: CompletionStats) {
    val zone = ZoneId.systemDefault()
    val scale = maxOf(stats.best, 1)
    Row(
        modifier = Modifier.fillMaxWidth().height(Sizes.chart),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        stats.weeks.forEachIndexed { index, week ->
            val isCurrent = index == stats.weeks.lastIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clearAndSetSemantics { contentDescription = spokenWeek(week, isCurrent, zone) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(week.count.toString(), style = MaterialTheme.typography.labelSmall)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(week.count.toFloat() / scale)
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                RoundedCornerShape(topStart = Radius.sm, topEnd = Radius.sm),
                            ),
                    )
                }
                Text(
                    SHORT_DATE.format(week.localDate(zone)),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun summary(stats: CompletionStats): String {
    val weeks = stats.weeks.size
    return when (stats.total) {
        0 -> "Aucune tâche terminée sur les $weeks dernières semaines"
        1 -> "1 tâche terminée sur les $weeks dernières semaines"
        else -> "${stats.total} tâches terminées sur les $weeks dernières semaines · meilleure semaine : ${stats.best}"
    }
}

private fun spokenWeek(week: WeekCompletion, isCurrent: Boolean, zone: ZoneId): String {
    val tasks = when (week.count) {
        0 -> "aucune tâche terminée"
        1 -> "1 tâche terminée"
        else -> "${week.count} tâches terminées"
    }
    val label = if (isCurrent) "Cette semaine" else "Semaine du ${LONG_DATE.format(week.localDate(zone))}"
    return "$label : $tasks"
}

/** Le lundi de la semaine, dans le fuseau de l'appareil (celui du calcul KMP). */
private fun WeekCompletion.localDate(zone: ZoneId) = Instant.ofEpochMilli(weekStart).atZone(zone).toLocalDate()

private val SHORT_DATE = DateTimeFormatter.ofPattern("d/MM", Locale.FRENCH)
private val LONG_DATE = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)
