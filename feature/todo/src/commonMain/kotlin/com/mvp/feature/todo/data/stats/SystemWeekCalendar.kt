package com.mvp.feature.todo.data.stats

import com.mvp.feature.todo.domain.calendar.WeekCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Semaines dans le fuseau [timeZone], lu à chaque appel : un changement de fuseau de l'appareil est pris en compte
 * au calcul suivant. Les semaines se comptent en jours du calendrier, pas en tranches de 7 × 24 h : une semaine
 * avec un changement d'heure dure 167 ou 169 h et commence toujours le lundi à 00:00.
 */
@OptIn(ExperimentalTime::class)
internal class SystemWeekCalendar(
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : WeekCalendar {

    override fun weekStartOf(instant: Long): Long {
        val zone = timeZone()
        val date = date(instant, zone)
        return date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY).startIn(zone)
    }

    override fun shiftWeeks(weekStart: Long, weeks: Int): Long {
        val zone = timeZone()
        return date(weekStart, zone).plus(weeks * DAYS_PER_WEEK, DateTimeUnit.DAY).startIn(zone)
    }

    private fun date(instant: Long, zone: TimeZone): LocalDate =
        Instant.fromEpochMilliseconds(instant).toLocalDateTime(zone).date

    private fun LocalDate.startIn(zone: TimeZone): Long = atStartOfDayIn(zone).toEpochMilliseconds()

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}
