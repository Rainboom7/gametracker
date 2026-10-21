package app.playlog.platform.deals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DealRecommenderTest {
    private val roguelite = mapOf("Roguelite" to 500, "Action" to 300, "Pixel Graphics" to 100)
    private val metroidvania = mapOf("Metroidvania" to 500, "Difficult" to 300)

    private fun deal(id: Long, title: String, review: Int? = 90, discount: Int = 50) = DealCandidate(id, title, discount, reviewPercent = review)

    @Test
    fun `vectors are unit length and damp generic tags`() {
        val v = DealRecommender.vector(mapOf("Roguelite" to 100, "Indie" to 100))
        assertEquals(1.0, v.values.sumOf { it * it }, 1e-9)
        assertTrue(v.getValue("Roguelite") > v.getValue("Indie"))
    }

    @Test
    fun `heavier seeds pull the ranking their way`() {
        val seeds = listOf(
            DealRecommender.TaggedSeed("Hades", weight = 4.0, tags = roguelite),
            DealRecommender.TaggedSeed("Hollow Knight", weight = 1.0, tags = metroidvania),
        )
        val ranked = DealRecommender.rank(
            seeds,
            listOf(deal(1, "Dead Cells") to roguelite, deal(2, "Ori") to metroidvania, deal(3, "FIFA") to mapOf("Sports" to 100)),
            limit = 10,
        )

        assertEquals(listOf("Dead Cells", "Ori"), ranked.map { it.candidate.title })
        assertEquals(listOf("Hades"), ranked.first().becauseOf)
        assertEquals("Roguelite", ranked.first().sharedTags.first())
    }

    @Test
    fun `reviews and discount break ties between equally similar games`() {
        val seeds = listOf(DealRecommender.TaggedSeed("Hades", 1.0, roguelite))
        val ranked = DealRecommender.rank(
            seeds,
            listOf(deal(1, "Meh", review = 72, discount = 20) to roguelite, deal(2, "Great", review = 97, discount = 80) to roguelite),
            limit = 10,
        )
        assertEquals("Great", ranked.first().candidate.title)
    }

    @Test
    fun `no taste means no picks`() {
        assertTrue(DealRecommender.rank(emptyList(), listOf(deal(1, "x") to roguelite), 10).isEmpty())
    }
}
