package app.playlog.core.model

import java.time.Instant

/**
 * A game the user does not own, on sale right now, picked because it resembles what they play.
 * Prices are display strings straight from the store (already in the user's currency).
 */
data class DealPick(
    val steamAppId: Long,
    val title: String,
    /** Wide store capsule, 460×215. */
    val headerUrl: String,
    /** Full-size store screenshots, best first. */
    val screenshots: List<String> = emptyList(),
    val shortDescription: String? = null,
    val discountPercent: Int,
    val priceText: String,
    val originalPriceText: String? = null,
    /** Steam user reviews, 0..100. */
    val reviewPercent: Int? = null,
    /** Library titles this deal is most similar to, best first. */
    val becauseOf: List<String> = emptyList(),
    /** Store tags shared with the user's taste, strongest first. */
    val sharedTags: List<String> = emptyList(),
    /** 0..1 similarity to the user's taste. */
    val match: Float = 0f,
) {
    val storeUrl: String get() = "https://store.steampowered.com/app/$steamAppId"
}

sealed interface DealsState {
    data object Loading : DealsState

    /** Nothing played yet, so there is no taste to match against. */
    data object NoTaste : DealsState

    data class Ready(val picks: List<DealPick>, val fetchedAt: Instant) : DealsState

    /** The stores could not be reached; [stale] holds the last good picks, if any. */
    data class Failed(val stale: List<DealPick>, val message: String? = null) : DealsState
}
