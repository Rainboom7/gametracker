package app.playlog.core.data

import app.playlog.core.common.AppClock
import app.playlog.core.model.Anniversary
import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.DiscoverShelf
import app.playlog.core.model.DiscoverSummary
import app.playlog.core.model.GameStatus
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Milestone
import app.playlog.core.model.MilestoneKind
import app.playlog.core.model.Pick
import app.playlog.core.model.PickReason
import app.playlog.core.model.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.random.Random

interface DiscoverRepository {
    fun observeSummary(): Flow<DiscoverSummary>

    /** A random title from the backlog and forgotten games, optionally on one platform. */
    suspend fun roulette(platform: Platform?, exclude: LibraryEntry? = null): LibraryEntry?
}

class DefaultDiscoverRepository(
    private val store: LibraryStore,
    private val library: LibraryRepository,
    private val clock: AppClock,
    private val random: Random = Random.Default,
) : DiscoverRepository {

    override fun observeSummary(): Flow<DiscoverSummary> {
        val today = today()
        return combine(library.observeLibrary(), store.observeDaily(today.minusDays(DAILY_WINDOW_DAYS), today)) { entries, daily ->
            DiscoverCalculator.summarize(entries, daily, today, clock.zone())
        }
    }

    override suspend fun roulette(platform: Platform?, exclude: LibraryEntry?): LibraryEntry? {
        val entries = library.observeLibrary().first()
        return DiscoverCalculator.roulette(entries, today(), clock.zone(), platform, exclude?.key, random)
    }

    private fun today() = LocalDate.ofInstant(clock.now(), clock.zone())

    private companion object {
        const val DAILY_WINDOW_DAYS = 366L
    }
}

/** Pure suggestion and badge logic over the merged library. */
object DiscoverCalculator {
    const val CONTINUE_WITHIN_DAYS = 30L
    const val RETURN_AFTER_DAYS = 90L
    val RETURN_MIN_PLAYTIME: Duration = Duration.ofHours(5)
    val BACKLOG_MAX_PLAYTIME: Duration = Duration.ofHours(1)
    val ALMOST_DONE_RANGE = 60..99
    const val ANNIVERSARY_WINDOW_DAYS = 3L
    private const val DAYS_PER_YEAR = 365L
    const val SHELF_SIZE = 10

    val THRESHOLDS: Map<MilestoneKind, List<Long>> = mapOf(
        MilestoneKind.MARATHON to listOf(10, 50, 100, 250, 500),
        MilestoneKind.VETERAN to listOf(100, 500, 1_000, 2_500, 5_000),
        MilestoneKind.COLLECTOR to listOf(10, 50, 100, 250, 500),
        MilestoneKind.FINISHER to listOf(1, 5, 10, 25, 50),
        MilestoneKind.PERFECTIONIST to listOf(1, 3, 10, 25),
        MilestoneKind.MULTIPLATFORM to listOf(1, 5, 10, 25),
        MilestoneKind.STREAK to listOf(3, 7, 14, 30, 100),
        MilestoneKind.BIG_DAY to listOf(3, 6, 10, 14),
        MilestoneKind.LOYAL to listOf(1, 3, 5, 10),
        MilestoneKind.PHOTOGRAPHER to listOf(10, 50, 200, 1_000),
        MilestoneKind.CRITIC to listOf(5, 25, 100),
    )

    private val SHELF_ORDER = listOf(PickReason.CONTINUE, PickReason.ALMOST_DONE, PickReason.RETURN_TO, PickReason.BACKLOG)

    /** A title goes to the first matching shelf in this order, so it never shows twice. */
    private val ASSIGN_ORDER = listOf(PickReason.ALMOST_DONE, PickReason.CONTINUE, PickReason.RETURN_TO, PickReason.BACKLOG)

    fun summarize(entries: List<LibraryEntry>, daily: List<DailyPlaytime>, today: LocalDate, zone: ZoneId): DiscoverSummary {
        val picks = picks(entries, today, zone)
        val byReason = picks.groupBy { it.reason }
        val shelves = SHELF_ORDER.mapNotNull { reason ->
            byReason[reason]?.let { list -> DiscoverShelf(reason, sortShelf(reason, list, today).take(SHELF_SIZE)) }
        }
        return DiscoverSummary(
            topPick = picks.maxByOrNull { it.score },
            shelves = shelves,
            milestones = milestones(entries, daily),
            anniversaries = anniversaries(entries, today, zone),
            backlogCount = entries.count { isBacklog(it) },
        )
    }

    fun picks(entries: List<LibraryEntry>, today: LocalDate, zone: ZoneId): List<Pick> = entries.mapNotNull { entry ->
        if (entry.status == GameStatus.COMPLETED || entry.status == GameStatus.DROPPED) return@mapNotNull null
        val days = entry.lastPlayedAt?.let { ChronoUnit.DAYS.between(LocalDate.ofInstant(it, zone), today) }
        val reason = ASSIGN_ORDER.firstOrNull { matches(it, entry, days) } ?: return@mapNotNull null
        Pick(entry, reason, score(reason, entry, days), days)
    }

    /** Uniform pick from backlog and forgotten titles; null when the pool is empty. */
    fun roulette(
        entries: List<LibraryEntry>,
        today: LocalDate,
        zone: ZoneId,
        platform: Platform?,
        excludeKey: String?,
        random: Random,
    ): LibraryEntry? {
        val pool = picks(entries, today, zone)
            .filter { it.reason == PickReason.BACKLOG || it.reason == PickReason.RETURN_TO }
            .map { it.entry }
            .filter { platform == null || platform in it.platforms }
        val candidates = pool.filter { it.key != excludeKey }.ifEmpty { pool }
        return if (candidates.isEmpty()) null else candidates[random.nextInt(candidates.size)]
    }

    /** Library-derived badges; [extra] adds the ones counted elsewhere (screenshots, ratings). */
    fun milestones(entries: List<LibraryEntry>, daily: List<DailyPlaytime>, extra: Map<MilestoneKind, Long> = emptyMap()): List<Milestone> {
        val values = extra + mapOf(
            MilestoneKind.MARATHON to (entries.maxOfOrNull { it.totalPlaytime.toHours() } ?: 0L),
            MilestoneKind.VETERAN to entries.fold(Duration.ZERO) { acc, e -> acc + e.totalPlaytime }.toHours(),
            MilestoneKind.COLLECTOR to entries.size.toLong(),
            MilestoneKind.FINISHER to entries.count { it.status == GameStatus.COMPLETED }.toLong(),
            MilestoneKind.PERFECTIONIST to entries.count { it.achievementProgress == 100 }.toLong(),
            MilestoneKind.MULTIPLATFORM to entries.count { it.platforms.size > 1 }.toLong(),
            MilestoneKind.STREAK to longestStreak(daily).toLong(),
            MilestoneKind.BIG_DAY to biggestDayHours(daily),
            MilestoneKind.LOYAL to (entries.maxOfOrNull(::loyalYears) ?: 0L),
        )
        return MilestoneKind.entries.mapNotNull { kind -> values[kind]?.let { Milestone(kind, it, THRESHOLDS.getValue(kind)) } }
    }

    private fun biggestDayHours(daily: List<DailyPlaytime>): Long =
        daily.groupBy { it.date }.values.maxOfOrNull { day -> day.fold(Duration.ZERO) { acc, d -> acc + d.playtime }.toHours() } ?: 0L

    private fun loyalYears(entry: LibraryEntry): Long {
        val first = entry.firstPlayedAt ?: return 0L
        val last = entry.lastPlayedAt ?: return 0L
        return ChronoUnit.DAYS.between(first, last).coerceAtLeast(0L) / DAYS_PER_YEAR
    }

    /** Longest run of consecutive days with any play. */
    fun longestStreak(daily: List<DailyPlaytime>): Int {
        val days = daily.filter { !it.playtime.isZero }.map { it.date }.distinct().sorted()
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in days) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    /** Titles first played this week (±[ANNIVERSARY_WINDOW_DAYS]) one or more years ago, oldest first. */
    fun anniversaries(entries: List<LibraryEntry>, today: LocalDate, zone: ZoneId): List<Anniversary> =
        entries.mapNotNull { entry ->
            val first = entry.firstPlayedAt?.let { LocalDate.ofInstant(it, zone) } ?: return@mapNotNull null
            val years = (today.year - first.year downTo 1).firstOrNull { y ->
                val date = first.plusYears(y.toLong())
                kotlin.math.abs(ChronoUnit.DAYS.between(date, today)) <= ANNIVERSARY_WINDOW_DAYS
            } ?: return@mapNotNull null
            Anniversary(entry, years)
        }.sortedWith(compareByDescending<Anniversary> { it.years }.thenByDescending { it.entry.totalPlaytime })

    private fun matches(reason: PickReason, entry: LibraryEntry, days: Long?): Boolean = when (reason) {
        PickReason.ALMOST_DONE -> entry.achievementProgress in ALMOST_DONE_RANGE
        PickReason.CONTINUE ->
            !entry.totalPlaytime.isZero && days != null &&
                (days <= CONTINUE_WITHIN_DAYS || (entry.status == GameStatus.PLAYING && days <= RETURN_AFTER_DAYS))
        PickReason.RETURN_TO -> entry.totalPlaytime >= RETURN_MIN_PLAYTIME && (days == null || days > RETURN_AFTER_DAYS)
        PickReason.BACKLOG -> isBacklog(entry)
    }

    private fun isBacklog(entry: LibraryEntry): Boolean =
        entry.status != GameStatus.COMPLETED && entry.status != GameStatus.DROPPED &&
            entry.totalPlaytime < BACKLOG_MAX_PLAYTIME &&
            // PSN reports zero minutes for PS4 titles that were actually played: no data is not backlog.
            !(entry.totalPlaytime.isZero && entry.games.all { "PS4" in it.devices })

    @Suppress("MagicNumber")
    private fun score(reason: PickReason, entry: LibraryEntry, days: Long?): Double {
        val favorite = if (entry.isFavorite) 15.0 else 0.0
        return favorite + when (reason) {
            PickReason.ALMOST_DONE -> 60.0 + (entry.achievementProgress ?: 0) * 0.4 - if ((days ?: 0) > 365) 20.0 else 0.0
            PickReason.CONTINUE -> 55.0 - (days ?: 0) + if (entry.status == GameStatus.PLAYING) 10.0 else 0.0
            PickReason.RETURN_TO -> 35.0 + minOf(entry.totalPlaytime.toHours(), 100L) * 0.2
            PickReason.BACKLOG -> 20.0
        }
    }

    private fun sortShelf(reason: PickReason, picks: List<Pick>, today: LocalDate): List<Pick> = when (reason) {
        PickReason.CONTINUE -> picks.sortedBy { it.daysSinceLastPlayed }
        PickReason.ALMOST_DONE -> picks.sortedByDescending { it.entry.achievementProgress }
        PickReason.RETURN_TO -> picks.sortedByDescending { it.entry.totalPlaytime }
        // Favorites first, then a shuffle that changes once a day so the shelf feels fresh.
        PickReason.BACKLOG -> picks.shuffled(Random(today.toEpochDay())).sortedByDescending { it.entry.isFavorite }
    }
}
