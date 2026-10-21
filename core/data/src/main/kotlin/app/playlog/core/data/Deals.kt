package app.playlog.core.data

import app.playlog.core.common.AppClock
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.model.DealPick
import app.playlog.core.model.DealsState
import app.playlog.core.model.GameStatus
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ln

/** One title the user played, weighted by how much it says about their taste. */
data class TasteSeed(
    val title: String,
    /** Known when the user owns it on Steam; other titles are looked up in the store by name. */
    val steamAppId: Long?,
    val weight: Double,
)

/** What the user already has, so deals never suggest a game they own on any platform. */
data class Ownership(val steamAppIds: Set<Long>, val titleKeys: Set<String>)

/**
 * Finds discounted games similar to [seeds]. Throws when the stores cannot be reached, so the
 * repository keeps showing the last good result instead of an empty list.
 */
fun interface DealsSource {
    /** [country] is the store region prices are shown in, as on the Deals screen. */
    suspend fun recommend(seeds: List<TasteSeed>, owned: Ownership, country: String): Recommendation
}

/** Picks plus the tag profile they were matched against. */
data class Recommendation(val picks: List<DealPick>, val taste: Map<String, Double> = emptyMap())

/** Last computed picks; survives restarts so the shelf shows instantly. */
interface DealsCache {
    suspend fun read(): CachedDeals?

    suspend fun write(deals: CachedDeals)
}

data class CachedDeals(
    val picks: List<DealPick>,
    val fetchedAt: Instant,
    val taste: Map<String, Double> = emptyMap(),
    /** Region the prices are in; picks from another region are refetched. */
    val country: String? = null,
)

class InMemoryDealsCache : DealsCache {
    @Volatile private var value: CachedDeals? = null

    override suspend fun read(): CachedDeals? = value

    override suspend fun write(deals: CachedDeals) {
        value = deals
    }
}

interface DealsRepository {
    /** Cached picks first, then a refresh when they are older than the TTL. */
    fun observe(): Flow<DealsState>

    /** Recomputes now, e.g. from pull-to-refresh. */
    suspend fun refresh()
}

class DefaultDealsRepository(
    private val source: DealsSource,
    private val library: LibraryRepository,
    private val cache: DealsCache,
    private val clock: AppClock,
    private val ttl: Duration = DEFAULT_TTL,
    private val onError: (Throwable) -> Unit = {},
    /** The Steam store region, from the account or the user's choice on the Deals screen. */
    private val country: suspend () -> String = { DEFAULT_COUNTRY },
) : DealsRepository {
    private val state = MutableStateFlow<DealsState>(DealsState.Loading)
    private val lock = Mutex()
    private var cacheLoaded = false
    private var stateCountry: String? = null

    override fun observe(): Flow<DealsState> = flow {
        coroutineScope {
            launch { update(force = false) }
            emitAll(state)
        }
    }

    override suspend fun refresh() = update(force = true)

    private suspend fun update(force: Boolean) = lock.withLock {
        if (!cacheLoaded) {
            cacheLoaded = true
            cache.read()?.let {
                state.value = DealsState.Ready(it.picks, it.fetchedAt, it.taste)
                stateCountry = it.country
            }
        }
        val current = state.value
        val region = runSuspendCatching { country() }.getOrDefault(DEFAULT_COUNTRY)
        val fresh = current is DealsState.Ready && current.fetchedAt.plus(ttl).isAfter(clock.now()) && stateCountry == region
        if (!force && fresh) return@withLock

        val entries = library.observeLibrary().first()
        val seeds = TasteProfile.seeds(entries, LocalDate.ofInstant(clock.now(), clock.zone()), clock.zone())
        if (seeds.isEmpty()) {
            state.value = DealsState.NoTaste
            return@withLock
        }
        runSuspendCatching { source.recommend(seeds, TasteProfile.ownership(entries), region) }
            .onSuccess { recommendation ->
                val result = CachedDeals(recommendation.picks, clock.now(), recommendation.taste, region)
                cache.write(result)
                stateCountry = region
                state.value = DealsState.Ready(result.picks, result.fetchedAt, result.taste)
            }
            .onFailure { e ->
                onError(e)
                val stale = (current as? DealsState.Ready)?.picks ?: (current as? DealsState.Failed)?.stale.orEmpty()
                state.value = DealsState.Failed(stale, e.message)
            }
    }

    companion object {
        /** Steam sales change daily at most; twice a day keeps the shelf fresh without hammering the stores. */
        val DEFAULT_TTL: Duration = Duration.ofHours(12)
        private const val DEFAULT_COUNTRY = "US"
    }
}

/** Turns the library into weighted taste seeds. Pure, so it is unit tested. */
object TasteProfile {
    const val MAX_SEEDS = 12
    private val MIN_PLAYTIME: Duration = Duration.ofHours(1)

    fun seeds(entries: List<LibraryEntry>, today: LocalDate, zone: ZoneId): List<TasteSeed> = entries
        .filter { it.totalPlaytime >= MIN_PLAYTIME }
        .map { entry -> TasteSeed(entry.title, entry.steamAppId(), weight(entry, today, zone)) }
        .filter { it.weight > 0.0 }
        .sortedByDescending { it.weight }
        .take(MAX_SEEDS)

    fun ownership(entries: List<LibraryEntry>) = Ownership(
        steamAppIds = entries.mapNotNullTo(HashSet()) { it.steamAppId() },
        titleKeys = entries.mapTo(HashSet()) { it.key },
    )

    @Suppress("MagicNumber")
    internal fun weight(entry: LibraryEntry, today: LocalDate, zone: ZoneId): Double {
        val hours = entry.totalPlaytime.toMinutes() / 60.0
        val days = entry.lastPlayedAt?.let { ChronoUnit.DAYS.between(LocalDate.ofInstant(it, zone), today) }
        val recency = when {
            days == null -> 0.6
            days <= 90 -> 1.0
            days <= 365 -> 0.75
            else -> 0.5
        }
        val status = when (entry.status) {
            GameStatus.DROPPED -> 0.3
            GameStatus.COMPLETED -> 1.1
            else -> 1.0
        }
        val favorite = if (entry.isFavorite) 1.5 else 1.0
        return ln(1 + hours) * recency * status * favorite
    }

    private fun LibraryEntry.steamAppId(): Long? =
        games.firstOrNull { it.id.platform == Platform.STEAM }?.id?.externalId?.toLongOrNull()
}

/** How well a store listing's genres or tags fit a taste profile. Pure, so it is unit tested. */
object TasteMatch {
    /** Tags arrive most relevant first; earlier ones weigh more, like vote counts would. */
    fun score(taste: Map<String, Double>, tags: List<String>): Float {
        if (taste.isEmpty() || tags.isEmpty()) return 0f
        val byKey = taste.mapKeys { it.key.lowercase() }
        val weights = tags.distinctBy { it.lowercase() }.mapIndexed { i, tag -> tag.lowercase() to 1.0 / (1 + i) }
        val norm = kotlin.math.sqrt(weights.sumOf { it.second * it.second })
        val dot = weights.sumOf { (tag, w) -> w * (byKey[tag] ?: 0.0) }
        return (dot / norm).toFloat().coerceIn(0f, 1f)
    }
}
