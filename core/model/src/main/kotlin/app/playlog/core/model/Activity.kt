package app.playlog.core.model

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
)

data class ProfileStats(
    val gameCount: Int,
    val totalPlaytime: Duration,
    /** Games the user marked [GameStatus.COMPLETED]. */
    val completedCount: Int,
)
