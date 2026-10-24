package com.mvp.feature.todo.domain.calendar

/**
 * Semaines du calendrier local, lundi en premier (ISO 8601). Heures en millisecondes depuis l'epoch (UTC).
 * L'app utilise le fuseau de l'appareil ; les tests, un calendrier fixe.
 */
interface WeekCalendar {
    /** Début (lundi 00:00 local) de la semaine qui contient [instant]. */
    fun weekStartOf(instant: Long): Long

    /** Début de la semaine [weeks] semaines après celle qui commence à [weekStart] (avant si [weeks] est négatif). */
    fun shiftWeeks(weekStart: Long, weeks: Int): Long
}
