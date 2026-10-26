package com.mvp.feature.todo.domain.calendar

/**
 * Jours du calendrier local (ADR 0022). Heures en millisecondes depuis l'epoch (UTC), jours en jours depuis le
 * 1970-01-01 ([CalendarDate.epochDay]). L'app utilise le fuseau de l'appareil ; les tests, un calendrier fixe.
 */
interface DayCalendar {
    /** Jour local qui contient [instant]. */
    fun dayOf(instant: Long): Int

    /** Début (00:00 local) du jour [epochDay]. */
    fun startOfDay(epochDay: Int): Long
}
