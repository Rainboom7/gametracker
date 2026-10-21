package app.playlog.core.data

import app.playlog.core.common.AppClock
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.model.Giveaway
import app.playlog.core.model.GiveawaysState
import app.playlog.core.model.TitleKey
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/** Free-to-keep games from every store it knows. Throws when nothing could be fetched. */
fun interface GiveawaysSource {
    suspend fun fetch(): List<Giveaway>
}

/** Last list plus the ids already announced, so a giveaway notifies once. */
interface GiveawaysStore {
    suspend fun read(): StoredGiveaways?

    suspend fun write(value: StoredGiveaways)
}

data class StoredGiveaways(val items: List<Giveaway>, val fetchedAt: Instant, val announced: Set<String> = emptySet())

class InMemoryGiveawaysStore : GiveawaysStore {
    @Volatile private var value: StoredGiveaways? = null

    override suspend fun read() = value

    override suspend fun write(value: StoredGiveaways) {
        this.value = value
    }
}

interface GiveawaysRepository {
    /** Cached list first, refreshed when older than the TTL; owned games are flagged. */
    fun observe(): Flow<GiveawaysState>

    suspend fun refresh()

    /** Refreshes and returns the live giveaways not announced before, marking them announced. */
    suspend fun takeNew(): List<Giveaway>
}

class DefaultGiveawaysRepository(
    private val source: GiveawaysSource,
    private val store: GiveawaysStore,
    private val library: LibraryRepository,
    private val clock: AppClock,
    private val ttl: Duration = DEFAULT_TTL,
    private val onError: (Throwable) -> Unit = {},
) : GiveawaysRepository {
    private val state = MutableStateFlow<GiveawaysState>(GiveawaysState.Loading)
    private val lock = Mutex()
    private var loaded = false

    override fun observe(): Flow<GiveawaysState> = flow {
        coroutineScope {
            launch { update(force = false) }
            emitAll(combine(state, library.observeLibrary()) { s, entries -> s.withOwned(entries.mapTo(HashSet()) { it.key }) })
        }
    }

    override suspend fun refresh() = update(force = true)

    override suspend fun takeNew(): List<Giveaway> {
        update(force = true)
        return lock.withLock {
            val stored = store.read() ?: return@withLock emptyList()
            val owned = library.observeLibrary().first().mapTo(HashSet()) { it.key }
            val now = clock.now()
            val fresh = stored.items.filter { g ->
                !g.upcoming && g.id !in stored.announced && TitleKey.of(g.title) !in owned && g.endsAt?.isAfter(now) != false
            }
            // Ids that left the feed are forgotten so the set does not grow forever.
            val live = stored.items.mapTo(HashSet()) { it.id }
            store.write(stored.copy(announced = stored.announced.filterTo(HashSet()) { it in live } + fresh.map { it.id }))
            fresh
        }
    }

    private suspend fun update(force: Boolean) = lock.withLock {
        val stored = if (!loaded) store.read().also { loaded = true } else null
        stored?.let { state.value = GiveawaysState.Ready(it.items, it.fetchedAt) }
        val current = state.value
        if (!force && current is GiveawaysState.Ready && current.fetchedAt.plus(ttl).isAfter(clock.now())) return@withLock
        runSuspendCatching { source.fetch() }
            .onSuccess { items ->
                val now = clock.now()
                val sorted = items.filter { it.endsAt?.isAfter(now) != false }
                    .sortedWith(compareBy<Giveaway> { it.upcoming }.thenBy { it.endsAt ?: Instant.MAX })
                val announced = (stored ?: store.read())?.announced.orEmpty()
                store.write(StoredGiveaways(sorted, now, announced))
                state.value = GiveawaysState.Ready(sorted, now)
            }
            .onFailure { e ->
                onError(e)
                state.value = GiveawaysState.Failed(current.itemsOrEmpty(), e.message)
            }
    }

    private fun GiveawaysState.itemsOrEmpty() = when (this) {
        is GiveawaysState.Ready -> items
        is GiveawaysState.Failed -> stale
        GiveawaysState.Loading -> emptyList()
    }

    private fun GiveawaysState.withOwned(owned: Set<String>): GiveawaysState {
        fun List<Giveaway>.mark() = map { it.copy(owned = TitleKey.of(it.title) in owned) }
        return when (this) {
            is GiveawaysState.Ready -> copy(items = items.mark())
            is GiveawaysState.Failed -> copy(stale = stale.mark())
            GiveawaysState.Loading -> this
        }
    }

    companion object {
        /** Giveaways start and end at fixed times a few times a week; a few hours is fresh enough. */
        val DEFAULT_TTL: Duration = Duration.ofHours(3)
    }
}
