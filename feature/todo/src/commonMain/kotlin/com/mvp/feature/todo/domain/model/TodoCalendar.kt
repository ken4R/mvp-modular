package com.mvp.feature.todo.domain.model

import com.mvp.feature.todo.domain.calendar.CalendarDate

/** Mois du calendrier demandé : [month] de 1 à 12 (ADR 0022). */
data class CalendarMonthRequest(val year: Int, val month: Int) {

    fun next(): CalendarMonthRequest = if (month == 12) CalendarMonthRequest(year + 1, 1) else copy(month = month + 1)

    fun previous(): CalendarMonthRequest = if (month == 1) CalendarMonthRequest(year - 1, 12) else copy(month = month - 1)
}

/**
 * Une case du calendrier. [isInMonth] : `false` pour les jours du mois précédent ou suivant qui complètent la
 * première et la dernière semaine. [todos] : tâches dont l'échéance tombe ce jour-là, à faire d'abord puis
 * terminées, chacune dans l'ordre de la liste. [isOverdue] : jour passé avec au moins une tâche à faire.
 */
data class CalendarDay(
    val date: CalendarDate,
    val epochDay: Int,
    val isInMonth: Boolean,
    val isToday: Boolean,
    val isOverdue: Boolean,
    val todos: List<Todo>,
) {
    val pendingCount: Int get() = todos.count { !it.isDone }
}

/**
 * Le mois [year]/[month] en semaines complètes, du lundi au dimanche : [days] en compte un multiple de 7 (4 à 6
 * semaines). [today] : aujourd'hui dans le fuseau de l'appareil. [overdueCount] : tâches à faire dont l'échéance
 * est passée, tous mois confondus ; [undatedCount] : tâches sans échéance, qui n'apparaissent pas dans le calendrier.
 */
data class CalendarMonth(
    val year: Int,
    val month: Int,
    val days: List<CalendarDay>,
    val today: CalendarDate,
    val overdueCount: Int,
    val undatedCount: Int,
)

/**
 * Événement à proposer au calendrier du téléphone pour une tâche (ADR 0022) : une journée entière, le jour [date]
 * de l'échéance. [title] tient sur une ligne ; [link] est le lien profond de la tâche. Jamais la note, ni les pièces
 * jointes : le calendrier du téléphone est synchronisé (iCloud, Google) et lisible par d'autres apps.
 */
data class TodoCalendarEvent(val title: String, val date: CalendarDate, val link: String)
