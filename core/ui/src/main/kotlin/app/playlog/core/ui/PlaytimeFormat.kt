package app.playlog.core.ui

import java.text.DecimalFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Pure formatting helpers; composable wrappers below pick the strings. */
object PlaytimeFormat {
    private val oneDecimal = DecimalFormat("0.#")

    sealed interface Value {
        data class Minutes(val minutes: Long) : Value
        data class Hours(val text: String) : Value
    }

    /** < 1 h → minutes; < 10 h → one decimal ("4,5"); otherwise whole hours. */
    fun of(duration: Duration): Value {
        val minutes = duration.toMinutes()
        if (minutes < MinutesPerHour) return Value.Minutes(minutes)
        val hours = minutes / MinutesPerHour.toDouble()
        return Value.Hours(if (hours < DecimalHoursLimit) oneDecimal.format(hours) else hours.toLong().toString())
    }

    /** Whole days between [instant] and [today] in [zone]; negative for the future. */
    fun daysAgo(instant: Instant, today: LocalDate, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(LocalDate.ofInstant(instant, zone), today)
}

fun Duration.toHoursFloat(): Float = toMinutes() / MinutesPerHour.toFloat()

private const val MinutesPerHour = 60
private const val DecimalHoursLimit = 10
