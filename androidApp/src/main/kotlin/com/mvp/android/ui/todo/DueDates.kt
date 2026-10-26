package com.mvp.android.ui.todo

import android.app.DatePickerDialog
import android.content.Context
import com.mvp.feature.todo.domain.calendar.CalendarDate
import com.mvp.feature.todo.domain.model.Todo
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Échéances (ADR 0022) : une date du calendrier, sans heure ni fuseau, comme `LocalDate`. */
internal fun CalendarDate.toLocalDate(): LocalDate = LocalDate.of(year, month, day)

internal fun dueLocalDate(epochDay: Int): LocalDate = LocalDate.ofEpochDay(epochDay.toLong())

/** « mer. 28 oct. 2026 » sous le titre d'une tâche. */
internal fun formatDueDate(epochDay: Int): String = SHORT_DUE_DATE.format(dueLocalDate(epochDay))

/** « Mercredi 28 octobre 2026 » dans la fiche. */
internal fun formatLongDueDate(epochDay: Int): String = LONG_DUE_DATE.format(dueLocalDate(epochDay)).capitalized()

/** Règle du retard de KMP (`Todo.isOverdue`), appliquée à la date du jour de l'appareil. */
internal fun Todo.isOverdueToday(): Boolean = isOverdue(LocalDate.now().toEpochDay().toInt())

internal fun String.capitalized(): String = replaceFirstChar { it.titlecase(Locale.FRENCH) }

/**
 * Sélecteur de date du système, de 2000 à 2100 (règle de `SetTodoDueDateUseCase`). Part de l'échéance existante,
 * sinon d'aujourd'hui ; une date passée est permise (tâche déjà en retard).
 */
internal fun pickDueDate(context: Context, current: Int?, onPicked: (Int) -> Unit) {
    val initial = current?.let(::dueLocalDate) ?: LocalDate.now()
    DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day).toEpochDay().toInt()) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).apply {
        val zone = ZoneId.systemDefault()
        datePicker.minDate = dueLocalDate(CalendarDate.MIN_EPOCH_DAY).atStartOfDay(zone).toInstant().toEpochMilli()
        datePicker.maxDate = dueLocalDate(CalendarDate.MAX_EPOCH_DAY).atStartOfDay(zone).toInstant().toEpochMilli()
    }.show()
}

private val SHORT_DUE_DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.FRENCH)
private val LONG_DUE_DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)
