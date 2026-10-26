package com.mvp.feature.todo.domain.calendar

/**
 * Date du calendrier grégorien, sans heure ni fuseau : une échéance « le 28 octobre » reste le 28 octobre quel que
 * soit le fuseau de l'appareil (ADR 0022). [month] de 1 à 12, [day] de 1 à 31. [epochDay] : jours depuis le
 * 1970-01-01, la forme stockée et synchronisée. Calcul sans bibliothèque (algorithme « days from civil » de
 * Howard Hinnant), pour que domain ne dépende de rien.
 */
data class CalendarDate(val year: Int, val month: Int, val day: Int) {

    val epochDay: Int get() = epochDayOf(year, month, day)

    /** Jour de la semaine ISO 8601 : 1 = lundi … 7 = dimanche. */
    val isoDayOfWeek: Int get() = isoDayOfWeek(epochDay)

    companion object {
        /** Bornes des échéances acceptées : du 1er janvier 2000 au 31 décembre 2100. */
        const val MIN_YEAR = 2000
        const val MAX_YEAR = 2100

        val MIN_EPOCH_DAY: Int = epochDayOf(MIN_YEAR, 1, 1)
        val MAX_EPOCH_DAY: Int = epochDayOf(MAX_YEAR, 12, 31)

        fun fromEpochDay(epochDay: Int): CalendarDate {
            val z = epochDay + DAYS_0000_TO_1970
            val era = (if (z >= 0) z else z - (DAYS_PER_ERA - 1)) / DAYS_PER_ERA
            val dayOfEra = z - era * DAYS_PER_ERA
            val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / (DAYS_PER_ERA - 1)) / 365
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val shiftedMonth = (5 * dayOfYear + 2) / 153
            val day = dayOfYear - (153 * shiftedMonth + 2) / 5 + 1
            val month = if (shiftedMonth < 10) shiftedMonth + 3 else shiftedMonth - 9
            val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
            return CalendarDate(year, month, day)
        }

        /** Nombre de jours du mois [month] (1 à 12) de l'année [year]. */
        fun daysInMonth(year: Int, month: Int): Int {
            val next = if (month == 12) epochDayOf(year + 1, 1, 1) else epochDayOf(year, month + 1, 1)
            return next - epochDayOf(year, month, 1)
        }

        /** `true` si [epochDay] est une échéance acceptée (entre [MIN_EPOCH_DAY] et [MAX_EPOCH_DAY]). */
        fun isValidDueDate(epochDay: Int): Boolean = epochDay in MIN_EPOCH_DAY..MAX_EPOCH_DAY

        // Le 1970-01-01 était un jeudi (4).
        internal fun isoDayOfWeek(epochDay: Int): Int = (epochDay + 3).mod(7) + 1

        private fun epochDayOf(year: Int, month: Int, day: Int): Int {
            // Années commençant le 1er mars : le 29 février tombe en fin d'année.
            val y = if (month <= 2) year - 1 else year
            val era = (if (y >= 0) y else y - 399) / 400
            val yearOfEra = y - era * 400
            val dayOfYear = (153 * ((month + 9) % 12) + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return era * DAYS_PER_ERA + dayOfEra - DAYS_0000_TO_1970
        }

        private const val DAYS_PER_ERA = 146_097
        private const val DAYS_0000_TO_1970 = 719_468
    }
}
