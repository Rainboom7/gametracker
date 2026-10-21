package app.playlog.platform.deals

import kotlin.math.sqrt

/**
 * Content-based matching on SteamSpy tags. Each game becomes a vector of tag vote shares; the
 * user's taste is the playtime-weighted sum of the vectors of games they played; a deal's match is
 * the cosine between the two. Pure, so it is unit tested without the network.
 */
object DealRecommender {
    data class TaggedSeed(val title: String, val weight: Double, val tags: Map<String, Int>)

    data class Scored(
        val candidate: DealCandidate,
        /** 0..1 cosine similarity to the taste. */
        val match: Float,
        val score: Double,
        val becauseOf: List<String>,
        val sharedTags: List<String>,
    )

    const val MIN_MATCH = 0.3
    private const val TOP_TAGS = 20
    private const val GENERIC_FACTOR = 0.3
    private const val SEED_REASON_MIN = 0.35
    private const val MATCH_WEIGHT = 0.75
    private const val REVIEW_WEIGHT = 0.15
    private const val DISCOUNT_WEIGHT = 0.10
    private const val DEFAULT_REVIEW = 0.75
    private const val PERCENT = 100.0
    private const val MAX_PERCENT = 100
    private const val MAX_REASONS = 2
    private const val MAX_TAGS = 3

    /** Tags almost every game has; they would make everything look alike. */
    internal val GENERIC = setOf(
        "Singleplayer", "Indie", "Action", "Adventure", "Great Soundtrack", "Atmospheric", "Casual", "2D", "3D",
        "Colorful", "Cute", "Story Rich", "Multiplayer", "Free to Play", "Early Access", "Classic", "Funny",
        "Difficult", "Controller", "Family Friendly", "Simulation", "Strategy", "RPG", "Fantasy", "Sci-fi",
    )

    fun vector(tags: Map<String, Int>): Map<String, Double> {
        val top = tags.entries.sortedByDescending { it.value }.take(TOP_TAGS)
        val total = top.sumOf { it.value }.toDouble()
        if (total <= 0) return emptyMap()
        return normalize(top.associate { (tag, votes) -> tag to votes / total * if (tag in GENERIC) GENERIC_FACTOR else 1.0 })
    }

    fun profile(seeds: List<TaggedSeed>): Map<String, Double> {
        val sum = HashMap<String, Double>()
        seeds.forEach { seed -> vector(seed.tags).forEach { (tag, v) -> sum.merge(tag, v * seed.weight, Double::plus) } }
        return normalize(sum)
    }

    fun cosine(a: Map<String, Double>, b: Map<String, Double>): Double {
        val (small, large) = if (a.size <= b.size) a to b else b to a
        return small.entries.sumOf { (tag, v) -> v * (large[tag] ?: 0.0) }
    }

    /**
     * A deal matches when it resembles the taste as a whole, or one played title strongly (scaled
     * by how much that title weighs): a mixed library still gets "more like Hollow Knight".
     */
    fun rank(seeds: List<TaggedSeed>, candidates: List<Pair<DealCandidate, Map<String, Int>>>, limit: Int): List<Scored> {
        val taste = profile(seeds)
        if (taste.isEmpty()) return emptyList()
        val maxWeight = seeds.maxOf { it.weight }
        val seedVectors = seeds.map { it to vector(it.tags) }
        return candidates.asSequence()
            .mapNotNull { (candidate, tags) ->
                val v = vector(tags)
                val perSeed = seedVectors.map { (seed, sv) -> seed to cosine(sv, v) }
                val bestSeed = perSeed.maxOfOrNull { (seed, sim) -> sim * sqrt(seed.weight / maxWeight) } ?: 0.0
                val match = maxOf(cosine(taste, v), bestSeed)
                if (match < MIN_MATCH) return@mapNotNull null
                val review = candidate.reviewPercent?.div(PERCENT) ?: DEFAULT_REVIEW
                val discount = candidate.discountPercent.coerceIn(0, MAX_PERCENT) / PERCENT
                Scored(
                    candidate = candidate,
                    match = match.toFloat(),
                    score = MATCH_WEIGHT * match + REVIEW_WEIGHT * review + DISCOUNT_WEIGHT * discount,
                    becauseOf = perSeed
                        .filter { it.second >= SEED_REASON_MIN }
                        .sortedByDescending { it.second }
                        .take(MAX_REASONS)
                        .map { it.first.title },
                    sharedTags = v.keys
                        .filter { it !in GENERIC && (taste[it] ?: 0.0) > 0.0 }
                        .sortedByDescending { (taste[it] ?: 0.0) * v.getValue(it) }
                        .take(MAX_TAGS),
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
    }

    private fun normalize(v: Map<String, Double>): Map<String, Double> {
        val norm = sqrt(v.values.sumOf { it * it })
        return if (norm == 0.0) emptyMap() else v.mapValues { it.value / norm }
    }
}
