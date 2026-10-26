package com.mvp.android.ui.todo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvp.android.ui.theme.MvpColors
import com.mvp.android.ui.theme.Radius
import com.mvp.android.ui.theme.Sizes
import com.mvp.android.ui.theme.Spacing
import com.mvp.feature.todo.domain.model.CalendarDay
import com.mvp.feature.todo.domain.model.CalendarMonth
import com.mvp.feature.todo.domain.model.Todo
import org.koin.androidx.compose.koinViewModel
import java.time.DayOfWeek
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Calendrier des tâches par date d'échéance (ADR 0022) : un mois en grille, du lundi au dimanche, et les tâches du
 * jour choisi en dessous. Affiché à la place de la liste, dans l'onglet Tâches : il reste derrière le verrou de
 * l'app. Un tap sur une tâche ouvre sa fiche ([onOpenTodo]), d'où l'on revient au calendrier.
 */
@Composable
fun TodoCalendarScreen(onClose: () -> Unit, onOpenTodo: (String) -> Unit, viewModel: TodoCalendarViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Retour") }
                Text(
                    "Calendrier",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(onClick = viewModel::onToday) { Text("Aujourd'hui") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = viewModel::onPreviousMonth, enabled = state.canShowPrevious) {
                    Text("‹", modifier = Modifier.semantics { contentDescription = "Mois précédent" })
                }
                Text(
                    monthTitle(state.request.year, state.request.month),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(onClick = viewModel::onNextMonth, enabled = state.canShowNext) {
                    Text("›", modifier = Modifier.semantics { contentDescription = "Mois suivant" })
                }
            }
            val month = state.month
            when {
                month != null -> {
                    if (month.overdueCount > 0) {
                        Text(
                            if (month.overdueCount == 1) "1 tâche en retard" else "${month.overdueCount} tâches en retard",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    MonthGrid(month, state.selectedDay, onSelect = viewModel::onSelectDay)
                    DayTodos(
                        day = state.selected,
                        undatedCount = month.undatedCount,
                        onToggle = { viewModel.onToggle(it) },
                        onOpen = onOpenTodo,
                    )
                }
                state.error -> Text(
                    "Calendrier indisponible",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(month: CalendarMonth, selectedDay: Int, onSelect: (CalendarDay) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // Initiales des jours, lues en entier par TalkBack dans chaque case : l'en-tête est ignoré.
        Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.FRENCH),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        month.days.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        selected = day.epochDay == selectedDay,
                        onSelect = { onSelect(day) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Case d'un jour : le numéro, puis le nombre de tâches (rouge si l'une est en retard, ✓ si toutes sont terminées).
 * Aujourd'hui sur le fond de mise en évidence, le jour choisi dans la couleur d'accent ; les jours du mois voisin
 * en petit.
 */
@Composable
private fun DayCell(day: CalendarDay, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        selected -> colors.primary
        day.isToday -> MvpColors.highlight
        else -> Color.Transparent
    }
    val content = if (selected) colors.onPrimary else colors.onSurface
    Column(
        modifier = modifier
            .height(Sizes.calendarDay)
            .background(background, RoundedCornerShape(Radius.md))
            .clickable(onClick = onSelect)
            // Une seule annonce par case ; l'action est redonnée à TalkBack, que clearAndSetSemantics retire.
            .clearAndSetSemantics {
                contentDescription = spokenDay(day)
                role = Role.Button
                this.selected = selected
                onClick {
                    onSelect()
                    true
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            day.date.day.toString(),
            color = content,
            style = if (day.isInMonth) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall,
            fontWeight = if (day.isToday) FontWeight.Bold else null,
        )
        if (day.todos.isNotEmpty()) {
            val marker = if (day.pendingCount == 0) "✓" else day.pendingCount.toString()
            val markerColor = when {
                selected -> colors.onPrimary
                day.isOverdue -> colors.error
                else -> colors.primary
            }
            Text(marker, color = markerColor, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DayTodos(day: CalendarDay?, undatedCount: Int, onToggle: (String) -> Unit, onOpen: (String) -> Unit) {
    if (day == null) return
    Text(
        dayTitle(day),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
    )
    if (day.todos.isEmpty()) {
        Text("Aucune tâche à cette date", style = MaterialTheme.typography.bodyLarge)
    }
    LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items(day.todos, key = { it.id }) { todo ->
            DayTodoRow(todo, overdue = day.isOverdue && !todo.isDone, onToggle = { onToggle(todo.id) }, onOpen = { onOpen(todo.id) })
        }
        if (undatedCount > 0) {
            item {
                Text(
                    if (undatedCount == 1) "1 tâche sans échéance n'apparaît pas ici" else "$undatedCount tâches sans échéance n'apparaissent pas ici",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun DayTodoRow(todo: Todo, overdue: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = todo.isDone, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f).clickable(onClickLabel = "Ouvrir la fiche", onClick = onOpen)) {
            Text(
                todo.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
            )
            if (overdue) Text("En retard", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun monthTitle(year: Int, month: Int): String =
    "${Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRENCH).capitalized()} $year"

private fun dayTitle(day: CalendarDay): String = DAY_TITLE.format(day.date.toLocalDate()).capitalized()

/** « Mercredi 28 octobre, 2 tâches dont 1 à faire, en retard, aujourd'hui ». */
private fun spokenDay(day: CalendarDay): String = buildList {
    add(DAY_TITLE.format(day.date.toLocalDate()).capitalized())
    add(
        when (day.todos.size) {
            0 -> "aucune tâche"
            1 -> if (day.pendingCount == 1) "1 tâche à faire" else "1 tâche terminée"
            else -> "${day.todos.size} tâches dont ${day.pendingCount} à faire"
        },
    )
    if (day.isOverdue) add("en retard")
    if (day.isToday) add("aujourd'hui")
}.joinToString(", ")

private val DAY_TITLE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

private const val DAYS_PER_WEEK = 7
