package com.mvp.feature.todo.data.calendar

import com.mvp.feature.todo.domain.calendar.DayCalendar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Jours dans le fuseau [timeZone], lu à chaque appel : un changement de fuseau de l'appareil est pris en compte au
 * calcul suivant. Un jour avec un changement d'heure dure 23 ou 25 h et commence au premier instant local du jour.
 */
@OptIn(ExperimentalTime::class)
internal class SystemDayCalendar(
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : DayCalendar {

    override fun dayOf(instant: Long): Int =
        Instant.fromEpochMilliseconds(instant).toLocalDateTime(timeZone()).date.toEpochDays().toInt()

    override fun startOfDay(epochDay: Int): Long =
        LocalDate.fromEpochDays(epochDay).atStartOfDayIn(timeZone()).toEpochMilliseconds()
}
