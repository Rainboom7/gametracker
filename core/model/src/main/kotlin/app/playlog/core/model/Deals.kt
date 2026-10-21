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
) {
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

    data class Ready(val picks: List<DealPick>, val fetchedAt: Instant) : DealsState

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
