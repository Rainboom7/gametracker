package app.playlog.core.data

import app.playlog.core.model.ActivityBucket
import app.playlog.core.model.ActivityPeriod
import app.playlog.core.model.ActivitySummary
import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import app.playlog.core.model.TopGame
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Pure aggregation of per-day playtime into chart buckets and top lists. */
object ActivityCalculator {

    data class Range(val start: LocalDate, val endInclusive: LocalDate)

    fun range(period: ActivityPeriod, anchor: LocalDate): Range = when (period) {
        ActivityPeriod.WEEK -> {
            val start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            Range(start, start.plusDays(6))
        }
        ActivityPeriod.MONTH -> Range(anchor.withDayOfMonth(1), anchor.with(TemporalAdjusters.lastDayOfMonth()))
        ActivityPeriod.YEAR -> Range(anchor.withDayOfYear(1), anchor.with(TemporalAdjusters.lastDayOfYear()))
    }

    fun previousAnchor(period: ActivityPeriod, anchor: LocalDate): LocalDate = when (period) {
        ActivityPeriod.WEEK -> anchor.minusWeeks(1)
        ActivityPeriod.MONTH -> anchor.minusMonths(1)
        ActivityPeriod.YEAR -> anchor.minusYears(1)
    }

    /**
     * @param daily rows covering at least the current and the previous period.
     * @param entries the merged library, used to attribute games to titles for the top list.
     */
    fun summarize(
        period: ActivityPeriod,
        anchor: LocalDate,
        daily: List<DailyPlaytime>,
        entries: List<LibraryEntry>,
        topCount: Int = 5,
    ): ActivitySummary {
        val range = range(period, anchor)
        val previous = range(period, previousAnchor(period, anchor))
        val inRange = daily.filter { it.date in range }
        val buckets = bucketRanges(period, range).map { bucket ->
            ActivityBucket(bucket.start, bucket.endInclusive, perPlatform(inRange.filter { it.date in bucket }))
        }
        val total = inRange.sum()
        return ActivitySummary(
            period = period,
            start = range.start,
            endInclusive = range.endInclusive,
            total = total,
            previousTotal = daily.filter { it.date in previous }.sum(),
            buckets = buckets,
            perPlatform = perPlatform(inRange),
            topGames = topGames(inRange, entries, total, topCount),
        )
    }

    /** One bucket per day for the last [days] days ending at [today], for the game detail chart. */
    fun lastDays(daily: List<DailyPlaytime>, today: LocalDate, days: Int): List<ActivityBucket> =
        (days - 1 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            ActivityBucket(day, day, perPlatform(daily.filter { it.date == day }))
        }

    /** Consecutive days with any play, ending today (or yesterday, so the streak survives until tonight). */
    fun currentStreak(daily: List<DailyPlaytime>, today: LocalDate): Int {
        val played = daily.filter { !it.playtime.isZero }.mapTo(HashSet()) { it.date }
        var day = if (today in played) today else today.minusDays(1)
        var streak = 0
        while (day in played) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    private fun bucketRanges(period: ActivityPeriod, range: Range): List<Range> = when (period) {
        ActivityPeriod.WEEK -> generateSequence(range.start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(range.endInclusive) }
            .map { Range(it, it) }
            .toList()
        ActivityPeriod.MONTH -> generateSequence(range.start) { it.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) }
            .takeWhile { !it.isAfter(range.endInclusive) }
            .map { start -> Range(start, minOf(start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)), range.endInclusive)) }
            .toList()
        ActivityPeriod.YEAR -> (0L until 12L).map { m ->
            val start = range.start.plusMonths(m)
            Range(start, start.with(TemporalAdjusters.lastDayOfMonth()))
        }
    }

    private fun topGames(inRange: List<DailyPlaytime>, entries: List<LibraryEntry>, total: Duration, count: Int): List<TopGame> {
        val entryByGame = entries.flatMap { e -> e.games.map { it.id to e } }.toMap()
        return inRange.groupBy { entryByGame[it.gameId] }
            .mapNotNull { (entry, rows) -> entry?.let { it to rows.sum() } }
            .filter { (_, time) -> !time.isZero }
            .sortedByDescending { (_, time) -> time }
            .take(count)
            .map { (entry, time) ->
                TopGame(entry, time, if (total.isZero) 0f else time.toMillis().toFloat() / total.toMillis())
            }
    }

    private fun perPlatform(rows: List<DailyPlaytime>): Map<Platform, Duration> =
        rows.groupBy { it.gameId.platform }.mapValues { (_, list) -> list.sum() }

    private fun List<DailyPlaytime>.sum(): Duration = fold(Duration.ZERO) { acc, d -> acc + d.playtime }

    private operator fun Range.contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(endInclusive)
}
