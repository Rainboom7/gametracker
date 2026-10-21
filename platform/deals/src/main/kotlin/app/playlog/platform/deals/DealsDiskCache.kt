package app.playlog.platform.deals

import app.playlog.core.data.CachedDeals
import app.playlog.core.data.DealsCache
import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import app.playlog.core.network.TrackerJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException
import java.time.Instant

/** Tags and name lookups barely change; caching them keeps a refresh to a handful of requests. */
interface TagStore {
    suspend fun tags(appId: Long): Map<String, Int>?

    suspend fun putTags(appId: Long, tags: Map<String, Int>)

    /** Steam appid for a normalized title; [NOT_ON_STEAM] when the lookup found nothing. */
    suspend fun appId(titleKey: String): Long?

    suspend fun putAppId(titleKey: String, appId: Long)

    /** Persists pending writes. */
    suspend fun flush() {}

    companion object {
        const val NOT_ON_STEAM = -1L
    }
}

class InMemoryTagStore : TagStore {
    private val tags = HashMap<Long, Map<String, Int>>()
    private val ids = HashMap<String, Long>()

    override suspend fun tags(appId: Long) = synchronized(this) { tags[appId] }
    override suspend fun putTags(appId: Long, tags: Map<String, Int>) = synchronized(this) { this.tags[appId] = tags }
    override suspend fun appId(titleKey: String) = synchronized(this) { ids[titleKey] }
    override suspend fun putAppId(titleKey: String, appId: Long) = synchronized(this) { ids[titleKey] = appId }
}

/**
 * Both caches as JSON files in the app's cache dir. Entries older than [maxAgeMillis] are
 * dropped on load. A corrupt file is deleted and rebuilt: everything here is re-fetchable.
 */
class DealsDiskCache(
    private val directory: File,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val maxAgeMillis: Long = THIRTY_DAYS,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TagStore, DealsCache {
    private val lock = Mutex()
    private var loaded: StoredTags? = null
    private var dirty = false

    override suspend fun tags(appId: Long): Map<String, Int>? = state { it.tags[appId.toString()]?.tags }

    override suspend fun putTags(appId: Long, tags: Map<String, Int>) = mutate { s ->
        s.copy(tags = s.tags + (appId.toString() to StoredTagEntry(nowMillis(), tags)))
    }

    override suspend fun appId(titleKey: String): Long? = state { it.appIds[titleKey]?.appId }

    override suspend fun putAppId(titleKey: String, appId: Long) = mutate { s ->
        s.copy(appIds = s.appIds + (titleKey to StoredAppId(nowMillis(), appId)))
    }

    override suspend fun flush() = lock.withLock {
        val s = loaded
        if (dirty && s != null) {
            write(TAGS_FILE, StoredTags.serializer(), s)
            dirty = false
        }
    }

    override suspend fun read(): CachedDeals? = withContext(io) {
        readFile(PICKS_FILE, StoredPicks.serializer())?.let { stored ->
            CachedDeals(stored.picks.map { it.toModel() }, Instant.ofEpochMilli(stored.fetchedAt))
        }
    }

    override suspend fun write(deals: CachedDeals) =
        write(PICKS_FILE, StoredPicks.serializer(), StoredPicks(deals.fetchedAt.toEpochMilli(), deals.picks.map(StoredPick::of)))

    private suspend fun <T> state(block: (StoredTags) -> T): T = lock.withLock { block(load()) }

    private suspend fun mutate(block: (StoredTags) -> StoredTags) = lock.withLock {
        loaded = block(load())
        dirty = true
    }

    private suspend fun load(): StoredTags = loaded ?: withContext(io) {
        val cutoff = nowMillis() - maxAgeMillis
        val s = readFile(TAGS_FILE, StoredTags.serializer()) ?: StoredTags()
        StoredTags(s.tags.filterValues { it.at >= cutoff }, s.appIds.filterValues { it.at >= cutoff })
    }.also { loaded = it }

    private fun <T> readFile(name: String, serializer: KSerializer<T>): T? {
        val file = File(directory, name)
        if (!file.exists()) return null
        return try {
            TrackerJson.decodeFromString(serializer, file.readText())
        } catch (@Suppress("SwallowedException") e: IOException) {
            null
        } catch (@Suppress("SwallowedException") e: SerializationException) {
            file.delete()
            null
        }
    }

    private suspend fun <T> write(name: String, serializer: KSerializer<T>, value: T) = withContext(io) {
        directory.mkdirs()
        val target = File(directory, name)
        val temp = File(directory, "$name.tmp")
        temp.writeText(TrackerJson.encodeToString(serializer, value))
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        Unit
    }

    private companion object {
        const val TAGS_FILE = "tags.json"
        const val PICKS_FILE = "picks-v2.json"
        const val THIRTY_DAYS = 30L * 24 * 60 * 60 * 1000
    }
}

@Serializable
internal data class StoredTags(
    val tags: Map<String, StoredTagEntry> = emptyMap(),
    val appIds: Map<String, StoredAppId> = emptyMap(),
)

@Serializable
internal data class StoredTagEntry(val at: Long, val tags: Map<String, Int>)

@Serializable
internal data class StoredAppId(val at: Long, val appId: Long)

@Serializable
internal data class StoredPicks(val fetchedAt: Long, val picks: List<StoredPick>)

@Serializable
internal data class StoredPick(
    val platform: Platform = Platform.STEAM,
    val storeId: String,
    val title: String,
    val header: String,
    val storeUrl: String,
    val screenshots: List<String> = emptyList(),
    val description: String? = null,
    val discount: Int,
    val price: String,
    val originalPrice: String? = null,
    val review: Int? = null,
    val endsAt: Long? = null,
    val becauseOf: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val match: Float = 0f,
) {
    fun toModel() = DealPick(
        platform, storeId, title, header, storeUrl, screenshots, description, discount, price, originalPrice, review,
        endsAt?.let(Instant::ofEpochMilli), becauseOf, tags, match,
    )

    companion object {
        fun of(p: DealPick) = StoredPick(
            p.platform, p.storeId, p.title, p.headerUrl, p.storeUrl, p.screenshots, p.shortDescription, p.discountPercent,
            p.priceText, p.originalPriceText, p.reviewPercent, p.endsAt?.toEpochMilli(), p.becauseOf, p.sharedTags, p.match,
        )
    }
}
