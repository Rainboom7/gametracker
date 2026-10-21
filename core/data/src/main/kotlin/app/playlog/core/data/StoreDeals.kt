package app.playlog.core.data

import app.playlog.core.common.runSuspendCatching
import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import app.playlog.core.model.RegionSource
import app.playlog.core.model.StoreDealsPage
import app.playlog.core.model.StoreDealsState
import app.playlog.core.model.StoreQuote
import app.playlog.core.model.StoreRegion
import app.playlog.core.model.TitleKey
import app.playlog.platform.api.GamePlatformSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** One platform store's current sale in a country, page by page. */
interface StoreDealsSource {
    val platform: Platform

    /** Countries this store can list, ISO alpha-2, most likely first. */
    val countries: List<String>

    /** Throws when the store cannot be reached or changed its format. */
    suspend fun page(country: String, page: Int): StoreDealsPage

    /**
     * Current listing of the given store ids, for wishlist price checks. Ids the store did not
     * answer for are left out, so "unknown" never reads as "not on sale".
     */
    suspend fun quotes(country: String, ids: List<String>): Map<String, StoreQuote> = emptyMap()

    /** Store search by title, on sale or not; empty when the store has no search. */
    suspend fun search(country: String, query: String): List<DealPick> = emptyList()
}

/** The user's manual region per platform; null means "from the account". */
interface DealsRegionStore {
    suspend fun get(platform: Platform): String?

    suspend fun set(platform: Platform, country: String?)
}

class InMemoryDealsRegionStore : DealsRegionStore {
    private val values = ConcurrentHashMap<Platform, String>()

    override suspend fun get(platform: Platform): String? = values[platform]

    override suspend fun set(platform: Platform, country: String?) {
        if (country == null) values.remove(platform) else values[platform] = country
    }
}

interface StoreDealsRepository {
    /** Platforms with a deals store, in tab order. */
    val platforms: List<Platform>

    fun countries(platform: Platform): List<String>

    /** First page on first collection; [loadMore] appends. */
    fun observe(platform: Platform): Flow<StoreDealsState>

    suspend fun loadMore(platform: Platform)

    suspend fun refresh(platform: Platform)

    /** A country, or null to go back to the account's region. Reloads the list. */
    suspend fun setRegion(platform: Platform, country: String?)

    /** The region deals and prices for [platform] are shown in. */
    suspend fun region(platform: Platform): StoreRegion

    /** Throws when the store cannot be reached. */
    suspend fun search(platform: Platform, query: String): List<DealPick>

    /** Throws when the store cannot be reached; see [StoreDealsSource.quotes]. */
    suspend fun quotes(platform: Platform, ids: List<String>): Map<String, StoreQuote>
}

/**
 * Region: manual choice, then the linked account's store country, then the device country.
 * A region the store has no catalogue for is reported as [StoreDealsState.Unsupported].
 */
class DefaultStoreDealsRepository(
    stores: Set<StoreDealsSource>,
    platformSources: Set<GamePlatformSource>,
    private val regions: DealsRegionStore,
    private val library: LibraryRepository,
    private val deviceCountry: () -> String,
    private val onError: (Platform, Throwable) -> Unit = { _, _ -> },
) : StoreDealsRepository {
    private val stores = stores.associateBy { it.platform }
    private val accounts = platformSources.associateBy { it.platform }

    override val platforms: List<Platform> = Platform.entries.filter { it in this.stores }

    private class Slot {
        val state = MutableStateFlow<StoreDealsState>(StoreDealsState.Loading)
        val lock = Mutex()
        var nextPage = 0
        var accountCountry: String? = null
        var accountChecked = false
    }

    private val slots = ConcurrentHashMap<Platform, Slot>()

    private fun slot(platform: Platform) = slots.getOrPut(platform) { Slot() }

    override fun countries(platform: Platform): List<String> = store(platform).countries

    override fun observe(platform: Platform): Flow<StoreDealsState> {
        val slot = slot(platform)
        return slot.state.onStart { if (slot.state.value is StoreDealsState.Loading) load(platform, reset = true) }
    }

    override suspend fun loadMore(platform: Platform) {
        val current = slot(platform).state.value
        if (current is StoreDealsState.Ready && current.hasMore && !current.loadingMore) load(platform, reset = false)
    }

    override suspend fun refresh(platform: Platform) = load(platform, reset = true)

    override suspend fun setRegion(platform: Platform, country: String?) {
        regions.set(platform, country)
        load(platform, reset = true)
    }

    override suspend fun region(platform: Platform): StoreRegion {
        val slot = slot(platform)
        return slot.lock.withLock { region(platform, slot) }
    }

    override suspend fun search(platform: Platform, query: String): List<DealPick> {
        val region = region(platform)
        if (region.country !in store(platform).countries) return emptyList()
        return store(platform).search(region.country, query.trim())
    }

    override suspend fun quotes(platform: Platform, ids: List<String>): Map<String, StoreQuote> {
        val region = region(platform)
        if (ids.isEmpty() || region.country !in store(platform).countries) return emptyMap()
        return store(platform).quotes(region.country, ids)
    }

    private suspend fun load(platform: Platform, reset: Boolean) {
        val slot = slot(platform)
        slot.lock.withLock {
            val region = region(platform, slot)
            val previous = (slot.state.value as? StoreDealsState.Ready)?.takeUnless { reset || it.region != region }
            if (previous == null) {
                slot.nextPage = 0
                slot.state.value = StoreDealsState.Loading
            } else {
                slot.state.value = previous.copy(loadingMore = true)
            }
            if (region.country !in store(platform).countries) {
                slot.state.value = StoreDealsState.Unsupported(region)
                return
            }
            runSuspendCatching { store(platform).page(region.country, slot.nextPage) }
                .onSuccess { page ->
                    slot.nextPage++
                    val owned = owned()
                    val fresh = page.deals.filterNot { TitleKey.of(it.title) in owned }
                    val deals = (previous?.deals.orEmpty() + fresh).distinctBy { it.key }
                    slot.state.value = StoreDealsState.Ready(region, deals, page.hasMore)
                }
                .onFailure { e ->
                    onError(platform, e)
                    slot.state.value = previous?.copy(loadingMore = false, hasMore = false) ?: StoreDealsState.Failed(region, e.message)
                }
        }
    }

    private suspend fun region(platform: Platform, slot: Slot): StoreRegion {
        regions.get(platform)?.let { return StoreRegion(it, RegionSource.MANUAL) }
        if (!slot.accountChecked) {
            slot.accountCountry = accounts[platform]?.let { runSuspendCatching { it.storeCountry() }.getOrNull() }
            slot.accountChecked = true
        }
        slot.accountCountry?.let { return StoreRegion(it, RegionSource.ACCOUNT) }
        return StoreRegion(deviceCountry().uppercase().ifEmpty { "US" }, RegionSource.DEVICE)
    }

    private suspend fun owned(): Set<String> = library.observeLibrary().first().mapTo(HashSet()) { it.key }

    private fun store(platform: Platform) = stores[platform] ?: error("No deals store for $platform")
}

/** Convenience for UI: the deals of a [StoreDealsState], whatever its kind. */
val StoreDealsState.dealsOrEmpty: List<DealPick>
    get() = (this as? StoreDealsState.Ready)?.deals.orEmpty()
