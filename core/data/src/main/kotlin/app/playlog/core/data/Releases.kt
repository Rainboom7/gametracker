package app.playlog.core.data

import app.playlog.core.common.AppClock
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.model.Platform
import app.playlog.core.model.ReleasesState
import app.playlog.core.model.UpcomingGame
import app.playlog.core.model.WishlistItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDate

/** Games a store lists as coming soon. Throws when the store cannot be reached. */
fun interface UpcomingSource {
    suspend fun upcoming(country: String): List<UpcomingGame>
}

interface ReleasesRepository {
    /** The last list right away, then a fetch when it is older than the TTL. */
    fun observe(): Flow<ReleasesState>

    suspend fun refresh()
}

/**
 * Merges every platform's upcoming list, each asked in that platform's store region. One store being
 * down keeps its previous games instead of failing the whole calendar.
 */
class DefaultReleasesRepository(
    private val sources: Map<Platform, UpcomingSource>,
    private val stores: StoreDealsRepository,
    private val clock: AppClock,
    private val ttl: Duration = DEFAULT_TTL,
    private val onError: (Throwable) -> Unit = {},
) : ReleasesRepository {
    private val state = MutableStateFlow<ReleasesState>(ReleasesState.Loading)
    private val lock = Mutex()

    override fun observe(): Flow<ReleasesState> = flow {
        coroutineScope {
            launch { update(force = false) }
            emitAll(state)
        }
    }

    override suspend fun refresh() = update(force = true)

    private suspend fun update(force: Boolean) = lock.withLock {
        val current = state.value
        if (!force && current is ReleasesState.Ready && current.fetchedAt.plus(ttl).isAfter(clock.now())) return@withLock
        val stale = when (current) {
            is ReleasesState.Ready -> current.games
            is ReleasesState.Failed -> current.stale
            ReleasesState.Loading -> emptyList()
        }
        val results = coroutineScope {
            sources.map { (platform, source) ->
                async { platform to runSuspendCatching { source.upcoming(stores.region(platform).country) } }
            }.awaitAll()
        }
        results.forEach { (_, r) -> r.exceptionOrNull()?.let(onError) }
        val failure = results.firstNotNullOfOrNull { it.second.exceptionOrNull() }
        if (results.none { it.second.isSuccess }) {
            state.value = ReleasesState.Failed(stale, failure?.message)
            return@withLock
        }
        val games = results.flatMap { (platform, r) -> r.getOrElse { stale.filter { it.platform == platform } } }
        state.value = ReleasesState.Ready(games, clock.now())
    }
}

private val DEFAULT_TTL: Duration = Duration.ofHours(6)

enum class ReleaseFilter { ALL, FOR_YOU, WISHLIST }

/** An upcoming game as the calendar shows it. [match] is 0..1 against the user's taste. */
data class ReleaseCard(val game: UpcomingGame, val match: Float, val sharedTags: List<String>, val wishlisted: Boolean)

data class ReleaseDay(val date: LocalDate, val games: List<ReleaseCard>)

data class ReleaseCalendar(
    val days: List<ReleaseDay>,
    /** Announced without a day: "Q1 2027", "Coming soon". */
    val undated: List<ReleaseCard>,
    /** Best match in the next week, for the hero card; null when nothing fits the taste. */
    val highlight: ReleaseCard?,
) {
    val isEmpty: Boolean get() = days.isEmpty() && undated.isEmpty()

    companion object {
        /** Above this a release counts as "for you". */
        const val FOR_YOU = 0.5f
        private const val SHARED_TAGS = 3
        private const val HIGHLIGHT_DAYS = 7L

        /**
         * Store releases plus coming-soon wishlist items (from any platform), from [today] on, by day.
         * [taste] is the tag profile from deals. A game scores the summed weight of the taste tags it
         * carries; [ReleaseCard.match] is that score relative to the best-scoring release, because
         * absolute scores depend on how spread out the taste is.
         */
        fun build(
            store: List<UpcomingGame>,
            wishlist: List<WishlistItem>,
            taste: Map<String, Double>,
            today: LocalDate,
            filter: ReleaseFilter,
            zone: java.time.ZoneId,
        ): ReleaseCalendar {
            val wishKeys = wishlist.mapTo(HashSet()) { it.key }
            val fromWishlist = wishlist.filter { it.comingSoon || it.releaseDate?.let { d -> LocalDate.ofInstant(d, zone) >= today } == true }
                .map { it.toUpcoming(zone) }
            val strength = taste.entries.associate { it.key.lowercase() to it.value }
            val games = (store + fromWishlist).distinctBy { it.key }.filter { g -> g.date.let { it == null || it >= today } }
            val raw = games.associate { game -> game.key to game.tags.sumOf { strength[it.lowercase()] ?: 0.0 } }
            val best = raw.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            val cards = games
                .map { game ->
                    val shared = game.tags.filter { strength.containsKey(it.lowercase()) }
                        .sortedByDescending { strength.getValue(it.lowercase()) }
                        .take(SHARED_TAGS)
                    ReleaseCard(game, (raw.getValue(game.key) / best).toFloat(), shared, game.key in wishKeys)
                }
                .filter {
                    when (filter) {
                        ReleaseFilter.ALL -> true
                        ReleaseFilter.FOR_YOU -> it.match >= FOR_YOU || it.wishlisted
                        ReleaseFilter.WISHLIST -> it.wishlisted
                    }
                }
            // Wishlisting must not move a card: when it floated to the top, the tapped row jumped out
            // of view and looked removed. The heart marks it; the Wishlist filter gathers them.
            val order = compareByDescending<ReleaseCard> { it.match }.thenBy { it.game.title }
            val days = cards.filter { it.game.date != null }
                .groupBy { it.game.date!! }
                .toSortedMap()
                .map { (date, list) -> ReleaseDay(date, list.sortedWith(order)) }
            val highlight = cards
                .filter { c -> c.game.date?.let { it < today.plusDays(HIGHLIGHT_DAYS) } == true && c.match >= FOR_YOU }
                .maxByOrNull { it.match }
            return ReleaseCalendar(days, cards.filter { it.game.date == null }.sortedWith(order), highlight)
        }

        private fun WishlistItem.toUpcoming(zone: java.time.ZoneId) = UpcomingGame(
            platform = platform,
            storeId = storeId,
            title = title,
            headerUrl = headerUrl,
            storeUrl = storeUrl,
            date = releaseDate?.let { LocalDate.ofInstant(it, zone) },
        )
    }
}
