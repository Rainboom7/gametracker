package app.playlog.feature.deals

import app.playlog.core.data.TasteMatch
import app.playlog.core.model.DealPick

enum class DealsSort { POPULAR, DISCOUNT, PRICE_LOW, PRICE_HIGH, RATING, MATCH, ENDING }

/** Client-side filters over the loaded store pages; the grid loads more pages as filtering thins them out. */
data class DealsFilter(
    val minDiscount: Int = 0,
    /** In the region's currency; null means any price. */
    val maxPrice: Double? = null,
    val genres: Set<String> = emptySet(),
    val similarOnly: Boolean = false,
    val sort: DealsSort = DealsSort.POPULAR,
) {
    /** Filters that hide something, for the badge on the filter button. */
    val activeCount: Int
        get() = listOf(minDiscount > 0, maxPrice != null, genres.isNotEmpty(), similarOnly).count { it }

    /** Region-specific parts (currency, genre vocabulary) do not carry over to another store. */
    fun forOtherStore() = copy(maxPrice = null, genres = emptySet())

    fun apply(deals: List<DealPick>, query: String): List<DealPick> {
        val q = query.trim()
        return sorted(deals.filter { matches(it) && (q.isEmpty() || it.title.contains(q, ignoreCase = true)) })
    }

    private fun matches(d: DealPick): Boolean {
        if (d.discountPercent < minDiscount) return false
        if (maxPrice != null && (d.priceValue ?: 0.0) > maxPrice) return false
        if (genres.isNotEmpty() && d.tags.none { it in genres }) return false
        return !similarOnly || d.match >= SIMILAR_MIN
    }

    private fun sorted(filtered: List<DealPick>): List<DealPick> =
        when (sort) {
            DealsSort.POPULAR -> filtered
            DealsSort.DISCOUNT -> filtered.sortedByDescending { it.discountPercent }
            DealsSort.PRICE_LOW -> filtered.sortedBy { it.priceValue ?: Double.MAX_VALUE }
            DealsSort.PRICE_HIGH -> filtered.sortedByDescending { it.priceValue ?: -1.0 }
            DealsSort.RATING -> filtered.sortedByDescending { it.reviewPercent ?: -1 }
            DealsSort.MATCH -> filtered.sortedByDescending { it.match }
            DealsSort.ENDING -> filtered.sortedBy { it.endsAt ?: java.time.Instant.MAX }
        }

    companion object {
        const val SIMILAR_MIN = 0.3f
        private const val TOP_GENRES = 16

        /** Scores listings that carry genres or tags against the user's taste; personal picks keep their own score. */
        fun withTaste(deals: List<DealPick>, taste: Map<String, Double>): List<DealPick> =
            if (taste.isEmpty()) deals else deals.map { d -> if (d.match > 0f || d.tags.isEmpty()) d else d.copy(match = TasteMatch.score(taste, d.tags)) }

        /** The most common genres among the loaded deals, for the filter chips. */
        fun topGenres(deals: List<DealPick>): List<String> = deals.flatMap { it.tags }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(TOP_GENRES)
            .map { it.key }
    }
}
