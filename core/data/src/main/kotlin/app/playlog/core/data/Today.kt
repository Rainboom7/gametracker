package app.playlog.core.data

import app.playlog.core.common.AppClock
import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.Giveaway
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Screenshot
import app.playlog.core.model.TodayItem
import app.playlog.core.model.WishlistItem
import app.playlog.core.model.items
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.temporal.ChronoUnit

interface TodayRepository {
    /** Memories first (oldest year leading), then offers that end soon; empty on an ordinary day. */
    fun observeToday(): Flow<List<TodayItem>>
}

class DefaultTodayRepository(
    private val store: LibraryStore,
    private val library: LibraryRepository,
    private val wishlist: WishlistRepository,
    private val giveaways: GiveawaysRepository,
    private val clock: AppClock,
) : TodayRepository {
    override fun observeToday(): Flow<List<TodayItem>> {
        val now = clock.now()
        val zone = clock.zone()
        val today = now.atZone(zone).toLocalDate()
        val free = giveaways.observe().map { it.items }.onStart { emit(emptyList()) }
        val memories = combine(
            library.observeLibrary(),
            store.observeDaily(today.minusYears(TodayCalculator.MAX_YEARS.toLong()), today.minusDays(1)),
            store.observeScreenshots(),
        ) { entries, daily, shots -> TodayCalculator.memories(entries, daily, shots, today, zone) }
        return combine(memories, wishlist.observe().onStart { emit(emptyList()) }, free) { past, wished, gifts ->
            past + TodayCalculator.endingSoon(wished, gifts, now)
        }
    }
}

object TodayCalculator {
    const val MAX_YEARS = 10
    private const val MAX_ITEMS_PER_KIND = 3
    val SALE_WINDOW: Duration = Duration.ofHours(48)
    val GIVEAWAY_WINDOW: Duration = Duration.ofHours(24)

    fun memories(
        entries: List<LibraryEntry>,
        daily: List<DailyPlaytime>,
        shots: List<Screenshot>,
        today: LocalDate,
        zone: ZoneId,
    ): List<TodayItem> {
        val day = MonthDay.from(today)
        val byGame = entries.flatMap { e -> e.games.map { it.id to e } }.toMap()
        val played = daily
            .filter { MonthDay.from(it.date) == day && it.date.year < today.year && !it.playtime.isZero }
            .mapNotNull { row -> byGame[row.gameId]?.let { TodayItem.PlayedOnThisDay(it, today.year - row.date.year, row.playtime) } }
            // One title can have rows from several platforms on the same day.
            .groupBy { it.entry.key to it.years }
            .map { (_, rows) -> rows.first().copy(playtime = rows.fold(Duration.ZERO) { acc, r -> acc + r.playtime }) }
            .sortedWith(compareByDescending<TodayItem.PlayedOnThisDay> { it.years }.thenByDescending { it.playtime })
            .take(MAX_ITEMS_PER_KIND)
        val captured = shots.asSequence()
            .mapNotNull { s -> s.takenAt?.let { it.atZone(zone).toLocalDate() }?.takeIf { MonthDay.from(it) == day && it.year < today.year }?.let { s to it } }
            .groupBy { (_, date) -> today.year - date.year }
            .map { (years, list) ->
                val inYear = list.map { it.first }
                TodayItem.ShotsOnThisDay(inYear, years, inYear.firstNotNullOfOrNull { s -> s.gameId?.let(byGame::get) })
            }
            .sortedByDescending { it.years }
            .take(MAX_ITEMS_PER_KIND)
        val anniversaries = entries.mapNotNull { entry ->
            val first = entry.firstPlayedAt?.let { it.atZone(zone).toLocalDate() } ?: return@mapNotNull null
            val years = (today.year - first.year).takeIf { it in 1..MAX_YEARS && MonthDay.from(first) == day } ?: return@mapNotNull null
            TodayItem.FirstPlayedAnniversary(entry, years)
        }.sortedByDescending { it.years }.take(MAX_ITEMS_PER_KIND)
        // A game already remembered by play or captures does not repeat as an anniversary.
        val remembered = played.map { it.entry.key }.toSet() + captured.mapNotNull { it.entry?.key }
        return captured + played + anniversaries.filter { it.entry.key !in remembered }
    }

    fun endingSoon(wishlist: List<WishlistItem>, giveaways: List<Giveaway>, now: Instant): List<TodayItem> {
        val sales = wishlist.filter { item -> item.isOnSale && item.price?.endsAt?.let { ends(it, now, SALE_WINDOW) } == true }
            .sortedBy { it.price?.endsAt }
            .map(TodayItem::SaleEndingSoon)
        val free = giveaways.filter { g -> !g.upcoming && !g.owned && g.endsAt?.let { ends(it, now, GIVEAWAY_WINDOW) } == true }
            .sortedBy { it.endsAt }
            .map(TodayItem::GiveawayEndingSoon)
        return (sales + free).take(MAX_ITEMS_PER_KIND)
    }

    private fun ends(at: Instant, now: Instant, window: Duration) = at.isAfter(now) && ChronoUnit.MILLIS.between(now, at) <= window.toMillis()
}
