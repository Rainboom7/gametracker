package app.playlog.core.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

/** When the Sunday-evening "your week" notification is due. */
object WeeklyDigest {
    private const val SUNDAY_FROM_HOUR = 18
    private const val MONDAY_UNTIL_HOUR = 12

    /**
     * The Monday of the week to sum up, or null when nothing is due. The window runs from Sunday
     * 18:00 to Monday noon, so a background run that slips past midnight still sends it, once.
     */
    fun dueWeek(now: ZonedDateTime, lastSentWeek: LocalDate?): LocalDate? {
        val week = when {
            now.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= SUNDAY_FROM_HOUR -> now.toLocalDate()
            now.dayOfWeek == DayOfWeek.MONDAY && now.hour < MONDAY_UNTIL_HOUR -> now.toLocalDate().minusDays(1)
            else -> return null
        }.with(DayOfWeek.MONDAY)
        return week.takeIf { lastSentWeek == null || it.isAfter(lastSentWeek) }
    }
}
