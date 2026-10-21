package app.playlog.feature.deals

import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class DealsFilterTest {
    private fun deal(id: String, discount: Int, price: Double?, tags: List<String> = emptyList(), review: Int? = null, ends: Long? = null) = DealPick(
        Platform.STEAM, id, "Game $id", "h", "u", discountPercent = discount, priceText = "$price", reviewPercent = review,
        endsAt = ends?.let(Instant::ofEpochSecond), priceValue = price, tags = tags,
    )

    private val deals = listOf(
        deal("a", 20, 100.0, listOf("Racing"), review = 70, ends = 30),
        deal("b", 75, 10.0, listOf("Roguelike", "Action"), review = 95, ends = 10),
        deal("c", 50, 40.0, listOf("Action"), review = null, ends = null),
    )

    private fun ids(list: List<DealPick>) = list.map { it.storeId }

    @Test
    fun `filters by discount, price, genre and title`() {
        assertEquals(listOf("b", "c"), ids(DealsFilter(minDiscount = 50).apply(deals, "")))
        assertEquals(listOf("b", "c"), ids(DealsFilter(maxPrice = 50.0).apply(deals, "")))
        assertEquals(listOf("b", "c"), ids(DealsFilter(genres = setOf("Action")).apply(deals, "")))
        assertEquals(listOf("a"), ids(DealsFilter().apply(deals, "game A")))
        assertEquals(3, DealsFilter(minDiscount = 50, maxPrice = 1.0, genres = setOf("x")).activeCount)
    }

    @Test
    fun `sorts every way`() {
        assertEquals(listOf("b", "c", "a"), ids(DealsFilter(sort = DealsSort.DISCOUNT).apply(deals, "")))
        assertEquals(listOf("b", "c", "a"), ids(DealsFilter(sort = DealsSort.PRICE_LOW).apply(deals, "")))
        assertEquals(listOf("a", "c", "b"), ids(DealsFilter(sort = DealsSort.PRICE_HIGH).apply(deals, "")))
        assertEquals(listOf("b", "a", "c"), ids(DealsFilter(sort = DealsSort.RATING).apply(deals, "")))
        assertEquals(listOf("b", "a", "c"), ids(DealsFilter(sort = DealsSort.ENDING).apply(deals, "")))
    }

    @Test
    fun `taste scores tagged deals and the similar filter keeps the close ones`() {
        val scored = DealsFilter.withTaste(deals, mapOf("roguelike" to 1.0))
        assertEquals(listOf("b"), ids(DealsFilter(similarOnly = true).apply(scored, "")))
        assertEquals("b", ids(DealsFilter(sort = DealsSort.MATCH).apply(scored, "")).first())
    }

    @Test
    fun `genres are ranked by how often they appear`() {
        assertEquals(listOf("Action", "Racing", "Roguelike"), DealsFilter.topGenres(deals).let { listOf(it[0]) + it.drop(1).sorted() })
    }

    @Test
    fun `switching store drops currency and genre filters`() {
        val f = DealsFilter(minDiscount = 50, maxPrice = 10.0, genres = setOf("Action"), sort = DealsSort.DISCOUNT).forOtherStore()
        assertEquals(DealsFilter(minDiscount = 50, sort = DealsSort.DISCOUNT), f)
    }
}
