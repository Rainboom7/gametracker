package app.playlog.core.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Total playtime of a game observed at a moment in time. Platforms mostly expose lifetime totals,
 * so the app records a snapshot on every sync and derives day-by-day activity from the deltas.
 */
data class PlaytimeSnapshot(
    val gameId: GameId,
    val observedAt: Instant,
    val totalPlaytime: Duration,
)

/** Time played on one calendar day (device time zone). */
data class DailyPlaytime(
    val gameId: GameId,
    val date: LocalDate,
    val playtime: Duration,
)

enum class ActivityPeriod { WEEK, MONTH, YEAR }

/** One bar of the activity chart: a day (week view), a week (month view) or a month (year view). */
data class ActivityBucket(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val perPlatform: Map<Platform, Duration>,
) {
    val total: Duration get() = perPlatform.values.fold(Duration.ZERO, Duration::plus)
}

data class TopGame(
    val entry: LibraryEntry,
    val playtime: Duration,
    /** Share of the period total, 0..1. */
    val share: Float,
)

data class ActivitySummary(
    val period: ActivityPeriod,
    val start: LocalDate,
    val endInclusive: LocalDate,
    val total: Duration,
    val previousTotal: Duration,
    val buckets: List<ActivityBucket>,
    val perPlatform: Map<Platform, Duration>,
    val topGames: List<TopGame>,
    /** Distinct titles (merged across platforms) played in the period. */
    val gamesPlayed: Int = 0,
    /** Days in the period with any playtime. */
    val activeDays: Int = 0,
    /** The day with the most playtime, null for an empty period. */
    val bestDay: DayTotal? = null,
)

data class DayTotal(val date: LocalDate, val playtime: Duration)

/** A year of play at a glance: the calendar heatmap, weekday rhythm and records. */
data class PlayInsights(
    /** Whole weeks, Monday first, ending with the current week; days after today are absent. */
    val days: List<DayTotal>,
    /** Average playtime per weekday over [days], counting days without play as zero. */
    val weekdayAverage: Map<DayOfWeek, Duration>,
    val activeDays: Int,
    val longestStreak: Int,
    val total: Duration,
)

data class ProfileStats(
    val gameCount: Int,
    val totalPlaytime: Duration,
    /** Games the user marked [GameStatus.COMPLETED]. */
    val completedCount: Int,
)
