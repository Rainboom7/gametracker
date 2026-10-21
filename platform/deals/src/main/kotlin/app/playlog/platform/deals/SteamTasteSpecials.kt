package app.playlog.platform.deals

import app.playlog.core.common.runSuspendCatching
import app.playlog.core.network.TrackerJson
import app.playlog.platform.deals.JsonWalk.string
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject

/**
 * Steam specials searched by the user's strongest taste tags, so "for you" candidates already share
 * what they play instead of being the store's generally popular sales. Search rows carry their
 * user tags in relevance order, so these candidates need no SteamSpy lookup.
 */
class SteamTasteSpecials(
    private val client: HttpClient,
    private val store: SteamStoreDeals,
    private val country: String,
    private val baseUrl: String = "https://store.steampowered.com",
    private val tagsPerSearch: Int = TAGS,
) {
    /** Candidates with synthetic tag votes (first tag strongest), for [DealRecommender.rank]. */
    suspend fun candidates(taste: Map<String, Double>): List<Pair<DealCandidate, Map<String, Int>>> {
        val names = store.tagNames()
        if (names.isEmpty()) return emptyList()
        val ids = names.entries.associate { (id, name) -> name.lowercase() to id }
        val strongest = taste.entries
            .filter { it.key !in DealRecommender.GENERIC }
            .sortedByDescending { it.value }
            .mapNotNull { ids[it.key.lowercase()] }
            .take(tagsPerSearch)
        return strongest.flatMap { tagId ->
            runSuspendCatching { search(tagId) }.getOrDefault(emptyList())
        }
            .distinctBy { it.appId }
            .map { row ->
                val candidate = DealCandidate(
                    appId = row.appId,
                    title = row.name,
                    // The regional discount is confirmed later from the store page; the badge only orders.
                    discountPercent = row.discount ?: OnlineDealsSource.MIN_DISCOUNT,
                    headerUrl = steamHeader(row.appId),
                    reviewPercent = row.review,
                )
                candidate to row.tagIds.mapNotNull(names::get).withIndex().associate { (i, tag) -> tag to (TOP_VOTES - i * VOTE_STEP).coerceAtLeast(1) }
            }
            .filter { it.second.isNotEmpty() }
    }

    private suspend fun search(tagId: Int): List<SteamStoreDeals.Row> {
        val response = client.get("$baseUrl/search/results/") {
            parameter("specials", 1)
            parameter("tags", tagId)
            parameter("category1", SteamStoreDeals.GAMES_CATEGORY)
            parameter("start", 0)
            parameter("count", SteamStoreDeals.PAGE_SIZE)
            parameter("cc", country)
            parameter("l", "english")
            parameter("infinite", 1)
        }
        if (!response.status.isSuccess()) throw DealsException("Steam taste specials HTTP ${response.status.value}")
        val body = TrackerJson.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return emptyList()
        return SteamStoreDeals.parseRows(body.string("results_html").orEmpty())
    }

    private companion object {
        const val TAGS = 4
        const val TOP_VOTES = 100
        const val VOTE_STEP = 8
    }
}
