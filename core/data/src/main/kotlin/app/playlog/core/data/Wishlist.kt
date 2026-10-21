package app.playlog.core.data

import app.playlog.core.common.AppClock
import app.playlog.core.common.runSuspendCatching
import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import app.playlog.core.model.PriceLow
import app.playlog.core.model.PriceDrop
import app.playlog.core.model.StorePrice
import app.playlog.core.model.StoreQuote
import app.playlog.core.model.WishlistItem
import app.playlog.platform.api.GamePlatformSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /** Adds the game, or removes it when it is already wishlisted. */
    suspend fun toggle(pick: DealPick)

    suspend fun remove(key: String)

    /** Adds the games on the linked accounts' own wishlists; returns how many were new. */
    suspend fun importFromAccounts(): Int

    /** Re-prices every item in its platform's region; returns the sales not notified yet. */
    suspend fun checkPrices(): List<PriceDrop>
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

    override suspend fun toggle(pick: DealPick) = update { items ->
        if (items.any { it.key == pick.key }) items.filterNot { it.key == pick.key } else listOf(pick.toItem()) + items
    }

    override suspend fun remove(key: String) = update { items -> items.filterNot { it.key == key } }

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

    override suspend fun checkPrices(): List<PriceDrop> {
        val drops = ArrayList<PriceDrop>()
        val snapshot = lock.withLock { load() }
        val quotes = snapshot.groupBy { it.platform }.mapValues { (platform, items) ->
            runSuspendCatching { stores.quotes(platform, items.map { it.storeId }) }.onFailure { onError(platform, it) }.getOrNull().orEmpty()
        }
        update { items ->
            items.map { item ->
                val quote = quotes[item.platform]?.get(item.storeId) ?: return@map item
                val price = quote.price ?: return@map item.copy(checkedAt = clock.now())
                val discount = price.discountPercent
                val checked = item.copy(
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
        return drops
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
    )
}

/** Lowest recorded price of a game; null when the service does not know it. */
fun interface PriceHistory {
    suspend fun lowest(platform: Platform, storeId: String): PriceLow?
}
