package app.playlog.core.data

import app.playlog.core.common.AppClock
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.model.GameMetadata
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/** What a metadata source needs to find a title. */
data class MetadataQuery(
    /** [LibraryEntry.key]; the cache key and the normalized title used for matching. */
    val key: String,
    val title: String,
    /** Set when the user owns the title on Steam: the store API is keyed by appid. */
    val steamAppId: Long? = null,
) {
    companion object {
        fun of(entry: LibraryEntry) = MetadataQuery(
            key = entry.key,
            title = entry.title,
            steamAppId = entry.games.firstOrNull { it.id.platform == Platform.STEAM }?.id?.externalId?.toLongOrNull(),
        )
    }
}

/**
 * Looks a title up on third-party services. Returns a record with empty fields when nothing
 * matched (cached like a hit, so misses are not retried on every visit) and throws when every
 * service failed, so a network outage is not cached as "unknown game".
 */
fun interface GameMetadataSource {
    suspend fun fetch(query: MetadataQuery): GameMetadata
}

interface MetadataStore {
    suspend fun get(key: String): GameMetadata?

    suspend fun put(metadata: GameMetadata)
}

interface GameMetadataRepository {
    /** Cached metadata for [entry], then a fresh copy when the cache is missing or stale. Emits null while nothing is known. */
    fun observe(entry: LibraryEntry): Flow<GameMetadata?>
}

class DefaultGameMetadataRepository(
    private val source: GameMetadataSource,
    private val store: MetadataStore,
    private val clock: AppClock,
    private val ttl: Duration = DEFAULT_TTL,
    private val onError: (MetadataQuery, Throwable) -> Unit = { _, _ -> },
) : GameMetadataRepository {
    // One lookup at a time: opening a game page twice must not fire duplicate scrapes.
    private val fetchLock = Mutex()

    override fun observe(entry: LibraryEntry): Flow<GameMetadata?> = flow {
        val cached = store.get(entry.key)
        emit(cached)
        if (cached != null && !isStale(cached)) return@flow
        val query = MetadataQuery.of(entry)
        val fresh = fetchLock.withLock {
            store.get(entry.key)?.takeUnless(::isStale) ?: fetch(query)?.also { store.put(it) }
        }
        if (fresh != null && fresh != cached) emit(fresh)
    }

    private suspend fun fetch(query: MetadataQuery): GameMetadata? =
        runSuspendCatching { source.fetch(query) }
            .onFailure { onError(query, it) }
            .getOrNull()

    private fun isStale(metadata: GameMetadata) = metadata.fetchedAt.plus(ttl).isBefore(clock.now())

    companion object {
        /** Playtime estimates and scores move slowly; two weeks keeps lookups rare. */
        val DEFAULT_TTL: Duration = Duration.ofDays(14)
    }
}

class InMemoryMetadataStore : MetadataStore {
    private val items = ConcurrentHashMap<String, GameMetadata>()

    override suspend fun get(key: String): GameMetadata? = items[key]

    override suspend fun put(metadata: GameMetadata) {
        items[metadata.key] = metadata
    }
}
