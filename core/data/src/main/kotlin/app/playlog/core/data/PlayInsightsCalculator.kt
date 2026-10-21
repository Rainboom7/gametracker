package app.playlog.core.data

import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.DayTotal
import app.playlog.core.model.PlayInsights
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Pure: per-day rows into the year calendar, weekday averages and the longest streak. */
object PlayInsightsCalculator {
    const val WEEKS = 53

    /** First day the calendar shows: the Monday [WEEKS] - 1 weeks before this week's. */
    fun start(today: LocalDate): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(WEEKS - 1L)

    fun insights(daily: List<DailyPlaytime>, today: LocalDate): PlayInsights {
        val from = start(today)
        val perDay = daily.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
            .groupBy { it.date }
            .mapValues { (_, rows) -> rows.fold(Duration.ZERO) { acc, r -> acc + r.playtime } }
        val days = generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .map { DayTotal(it, perDay[it] ?: Duration.ZERO) }
            .toList()
        val weekdayAverage = days.groupBy { it.date.dayOfWeek }.mapValues { (_, list) ->
            list.fold(Duration.ZERO) { acc, d -> acc + d.playtime }.dividedBy(list.size.toLong())
        }
        return PlayInsights(
            days = days,
            weekdayAverage = DayOfWeek.entries.associateWith { weekdayAverage[it] ?: Duration.ZERO },
            activeDays = days.count { !it.playtime.isZero },
            longestStreak = longestStreak(days),
            total = days.fold(Duration.ZERO) { acc, d -> acc + d.playtime },
        )
    }

    private fun longestStreak(days: List<DayTotal>): Int {
        var best = 0
        var run = 0
        for (d in days) {
            run = if (d.playtime.isZero) 0 else run + 1
            best = maxOf(best, run)
        }
        return best
    }
}
