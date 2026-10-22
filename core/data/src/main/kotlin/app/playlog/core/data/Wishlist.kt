package app.playlog.core.data

import app.playlog.core.common.AppClock
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import app.playlog.core.model.PriceLow
import app.playlog.core.model.PriceDrop
import app.playlog.core.model.StorePrice
import app.playlog.core.model.StoreQuote
import app.playlog.core.model.TitleKey
import app.playlog.core.model.WishMatcher
import app.playlog.core.model.WishlistCheck
import app.playlog.core.model.WishlistGame
import app.playlog.core.model.WishlistItem
import app.playlog.platform.api.GamePlatformSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

interface WishlistStore {
    suspend fun read(): List<WishlistItem>

    suspend fun write(items: List<WishlistItem>)
}

class InMemoryWishlistStore : WishlistStore {
    @Volatile private var items: List<WishlistItem> = emptyList()

    override suspend fun read() = items

    override suspend fun write(items: List<WishlistItem>) {
        this.items = items
    }
}

interface WishlistRepository {
    /** Newest first. */
    fun observe(): Flow<List<WishlistItem>>

    /** The list grouped into games: one entry per title across stores. */
    fun observeGames(): Flow<List<WishlistGame>> = observe().map(WishlistGame::group)

    /**
     * Adds the game, or removes it when it (or the same title in another store) is already
     * wishlisted. A new wish is then looked up in the other stores by title, so its price is tracked
     * everywhere. Pass [comingSoon] and [releaseDate] for unreleased games so the release is announced.
     */
    suspend fun toggle(pick: DealPick, comingSoon: Boolean = false, releaseDate: Instant? = null)

    /** Removes the item with [key] and every copy of the same game in other stores. */
    suspend fun remove(key: String)

    /** Adds the games on the linked accounts' own wishlists; returns how many were new. */
    suspend fun importFromAccounts(): Int

    /** Re-prices every item in its platform's region; returns the sales not notified yet. */
    suspend fun checkPrices(): List<PriceDrop> = check().drops

    /** [checkPrices] plus release tracking: games that were coming soon and are out now. */
    suspend fun check(): WishlistCheck
}

class DefaultWishlistRepository(
    private val store: WishlistStore,
    private val stores: StoreDealsRepository,
    platformSources: Set<GamePlatformSource>,
    private val clock: AppClock,
    private val onError: (Platform, Throwable) -> Unit = { _, _ -> },
) : WishlistRepository {
    private val accounts = platformSources.associateBy { it.platform }
    private val state = MutableStateFlow<List<WishlistItem>?>(null)
    private val lock = Mutex()

    override fun observe(): Flow<List<WishlistItem>> = state.onStart { lock.withLock { load() } }.filterNotNull()

    override suspend fun toggle(pick: DealPick, comingSoon: Boolean, releaseDate: Instant?) {
        val titleKey = TitleKey.of(pick.title)
        var added: WishlistItem? = null
        update { items ->
            if (pick in WishMatcher(items)) {
                items.filterNot { it.key == pick.key || it.titleKey == titleKey }
            } else {
                val item = pick.toItem().copy(comingSoon = comingSoon, releaseDate = releaseDate)
                added = item
                listOf(item) + items
            }
        }
        added?.let { linkAcrossStores(it) }
    }

    override suspend fun remove(key: String) = update { items ->
        val titleKey = items.firstOrNull { it.key == key }?.titleKey
        items.filterNot { it.key == key || (titleKey != null && it.titleKey == titleKey) }
    }

    /**
     * Finds the same game in the other stores by normalized title and adds those copies, so one wish
     * tracks every store's price. Only exact [TitleKey] matches count: a wrong link would alert on
     * someone else's sale. Failures just leave the wish on its own store.
     */
    private suspend fun linkAcrossStores(item: WishlistItem) {
        val found = stores.platforms.filter { it != item.platform }.mapNotNull { platform ->
            runSuspendCatching { stores.search(platform, item.title) }.onFailure { onError(platform, it) }.getOrNull()
                ?.firstOrNull { it.title.isNotBlank() && TitleKey.of(it.title) == item.titleKey }
        }
        if (found.isEmpty()) return
        update { items ->
            // The wish may have been removed while the stores answered.
            if (items.none { it.key == item.key }) return@update items
            val known = items.mapTo(HashSet()) { it.key }
            val copies = found.filter { it.key !in known }.map {
                it.toItem().copy(linked = true, comingSoon = item.comingSoon && it.priceText.isBlank(), releaseDate = item.releaseDate)
            }
            val at = items.indexOfFirst { it.key == item.key } + 1
            items.take(at) + copies + items.drop(at)
        }
    }

    override suspend fun importFromAccounts(): Int {
        var added = 0
        for (platform in stores.platforms) {
            val account = accounts[platform] ?: continue
            val ids = runSuspendCatching { account.wishlist() }.onFailure { onError(platform, it) }.getOrNull().orEmpty()
            val known = lock.withLock { load() }.filter { it.platform == platform }.mapTo(HashSet()) { it.storeId }
            val fresh = ids.filterNot { it in known }
            if (fresh.isEmpty()) continue
            val quotes = runSuspendCatching { stores.quotes(platform, fresh) }.onFailure { onError(platform, it) }.getOrNull().orEmpty()
            val items = fresh.mapNotNull { id -> quotes[id]?.toItem(platform, id) }
            added += items.size
            update { current -> current + items.filterNot { item -> current.any { it.key == item.key } } }
        }
        return added
    }

    override suspend fun check(): WishlistCheck {
        val drops = ArrayList<PriceDrop>()
        val released = ArrayList<WishlistItem>()
        val snapshot = lock.withLock { load() }
        val quotes = snapshot.groupBy { it.platform }.mapValues { (platform, items) ->
            runSuspendCatching { stores.quotes(platform, items.map { it.storeId }) }.onFailure { onError(platform, it) }.getOrNull().orEmpty()
        }
        update { items ->
            items.map { item ->
                val quote = quotes[item.platform]?.get(item.storeId) ?: return@map item
                val dated = item.copy(
                    comingSoon = quote.comingSoon ?: item.comingSoon,
                    releaseDate = quote.releaseDate ?: item.releaseDate,
                )
                if (item.comingSoon && quote.comingSoon == false) released += dated
                val price = quote.price ?: return@map dated.copy(checkedAt = clock.now())
                val discount = price.discountPercent
                val checked = dated.copy(
                    title = item.title.ifBlank { quote.title.orEmpty() },
                    headerUrl = item.headerUrl.ifBlank { quote.headerUrl.orEmpty() },
                    price = price,
                    checkedAt = clock.now(),
                    notifiedDiscount = if (discount == 0) 0 else maxOf(item.notifiedDiscount, discount),
                )
                if (discount > item.notifiedDiscount) drops += PriceDrop(checked, price)
                checked
            }
        }
        return WishlistCheck(drops, released)
    }

    private suspend fun load(): List<WishlistItem> = state.value ?: store.read().also { state.value = it }

    private suspend fun update(block: (List<WishlistItem>) -> List<WishlistItem>) = lock.withLock {
        val next = block(load())
        store.write(next)
        state.value = next
    }

    private fun DealPick.toItem() = WishlistItem(
        platform = platform,
        storeId = storeId,
        title = title,
        headerUrl = headerUrl,
        storeUrl = storeUrl,
        addedAt = clock.now(),
        price = priceText.takeIf { it.isNotBlank() }?.let { StorePrice(it, originalPriceText, discountPercent, priceValue, endsAt) },
        checkedAt = clock.now(),
        // Already on sale when added: the user has seen this one, alert on a deeper cut or the next sale.
        notifiedDiscount = discountPercent,
    )

    private fun StoreQuote.toItem(platform: Platform, id: String): WishlistItem? = WishlistItem(
        platform = platform,
        storeId = id,
        title = title ?: return null,
        headerUrl = headerUrl.orEmpty(),
        storeUrl = storeUrl ?: return null,
        addedAt = clock.now(),
        price = price,
        checkedAt = clock.now(),
        notifiedDiscount = price?.discountPercent ?: 0,
        comingSoon = comingSoon == true,
        releaseDate = releaseDate,
    )
}

/** Lowest recorded price of a game; null when the service does not know it. */
fun interface PriceHistory {
    suspend fun lowest(platform: Platform, storeId: String): PriceLow?
}
