package app.playlog.core.model

import java.time.Instant

/**
 * A discounted game in one platform's store. Prices are display strings from the store, already in
 * the region's currency. The personalization fields are set only for "for you" picks.
 */
data class DealPick(
    val platform: Platform,
    /** Store id: Steam appid, PlayStation product id, Nintendo nsuid. */
    val storeId: String,
    val title: String,
    /** Wide capsule / key art. */
    val headerUrl: String,
    val storeUrl: String,
    /** Full-size store screenshots, best first. */
    val screenshots: List<String> = emptyList(),
    val shortDescription: String? = null,
    val discountPercent: Int,
    val priceText: String,
    val originalPriceText: String? = null,
    /** Store user reviews, 0..100. */
    val reviewPercent: Int? = null,
    /** When the sale ends, if the store says. */
    val endsAt: Instant? = null,
    /** Library titles this deal is most similar to, best first. */
    val becauseOf: List<String> = emptyList(),
    /** Store tags shared with the user's taste, strongest first. */
    val sharedTags: List<String> = emptyList(),
    /** 0..1 similarity to the user's taste; 0 for plain store listings. */
    val match: Float = 0f,
    /** Sale price as a number in the region's currency, for filtering and sorting. */
    val priceValue: Double? = null,
    /** Store genres or user tags, most relevant first. */
    val tags: List<String> = emptyList(),
) {
    val isOnSale: Boolean get() = discountPercent > 0

    /** Stable key across platforms for lists. */
    val key: String get() = "${platform.name}:$storeId"

    companion object {
        fun steamStoreUrl(appId: Long) = "https://store.steampowered.com/app/$appId"
    }
}

sealed interface DealsState {
    data object Loading : DealsState

    /** Nothing played yet, so there is no taste to match against. */
    data object NoTaste : DealsState

    /** [taste] is the user's tag profile (tag → weight, unit length), for matching other deals. */
    data class Ready(val picks: List<DealPick>, val fetchedAt: Instant, val taste: Map<String, Double> = emptyMap()) : DealsState

    /** The stores could not be reached; [stale] holds the last good picks, if any. */
    data class Failed(val stale: List<DealPick>, val message: String? = null) : DealsState
}

/** Where the store region came from, shown next to it so the user knows what to change. */
enum class RegionSource { ACCOUNT, MANUAL, DEVICE }

data class StoreRegion(val country: String, val source: RegionSource)

/** One page of a platform store's current sale. */
data class StoreDealsPage(val deals: List<DealPick>, val hasMore: Boolean)

sealed interface StoreDealsState {
    data object Loading : StoreDealsState

    data class Ready(
        val region: StoreRegion,
        val deals: List<DealPick>,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
    ) : StoreDealsState

    /** The store has no sale listing for this region (e.g. closed storefront). */
    data class Unsupported(val region: StoreRegion) : StoreDealsState

    data class Failed(val region: StoreRegion, val message: String?) : StoreDealsState
}

/** A product's current price in one region; [discountPercent] 0 means full price. */
data class StorePrice(
    val priceText: String,
    val originalPriceText: String? = null,
    val discountPercent: Int = 0,
    val priceValue: Double? = null,
    val endsAt: Instant? = null,
)

/** What a store knows about one product right now; any part may be missing. */
data class StoreQuote(
    val title: String? = null,
    val headerUrl: String? = null,
    val storeUrl: String? = null,
    val price: StorePrice? = null,
    /** True before release, false once out, null when the store does not say. */
    val comingSoon: Boolean? = null,
    val releaseDate: Instant? = null,
)

/** A game the user wants, with the last price seen in their region. */
data class WishlistItem(
    val platform: Platform,
    val storeId: String,
    val title: String,
    val headerUrl: String,
    val storeUrl: String,
    val addedAt: Instant,
    val price: StorePrice? = null,
    val checkedAt: Instant? = null,
    /** Deepest discount already notified, so one sale alerts once; reset when the sale ends. */
    val notifiedDiscount: Int = 0,
    /** Not out yet as of the last check; flips to false on release, which is announced once. */
    val comingSoon: Boolean = false,
    val releaseDate: Instant? = null,
) {
    val key: String get() = "${platform.name}:$storeId"

    val isOnSale: Boolean get() = (price?.discountPercent ?: 0) > 0
}

/** A wishlisted game that went on sale or got cheaper since the last check. */
data class PriceDrop(val item: WishlistItem, val price: StorePrice)

/** One wishlist check: new sales and games that came out since the last check. */
data class WishlistCheck(val drops: List<PriceDrop>, val released: List<WishlistItem>)

/** Lowest price ever recorded for a game, from a price history service. */
data class PriceLow(val priceText: String, val at: Instant?, val source: String)
