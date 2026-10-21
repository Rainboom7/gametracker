package app.playlog.platform.deals

import app.playlog.core.common.runSuspendCatching
import app.playlog.core.data.FinderQuery
import app.playlog.core.data.GameFinder
import app.playlog.core.model.DealPick
import app.playlog.core.model.PlayFeature
import app.playlog.core.model.TitleKey
import app.playlog.core.network.TrackerJson
import app.playlog.platform.deals.JsonWalk.string
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject

/**
 * "Спроси" search over the whole Steam catalog, not just specials: user tags (`tags=` by id) and
 * play modes (`category3=`) narrow the search, rows come back with tags and review scores, and one
 * appdetails call prices them in the user's region. [tagsOf] reads SteamSpy for "games like X".
 */
class SteamTagSearch(
    private val client: HttpClient,
    private val store: SteamStoreDeals,
    private val spy: SteamSpyClient,
    private val baseUrl: String = "https://store.steampowered.com",
    private val log: (String) -> Unit = {},
) : GameFinder {

    override suspend fun find(query: FinderQuery): List<DealPick> {
        if (query.tags.isEmpty() && query.features.isEmpty() && query.text.isBlank()) return emptyList()
        val names = store.tagNames()
        val ids = names.entries.associate { (id, name) -> name.lowercase() to id }
        // A tag Steam does not know would make the search match nothing; drop it instead.
        val tagIds = query.tags.mapNotNull { ids[it.lowercase()] }
        val rows = mutableListOf<SteamStoreDeals.Row>()
        var page = 0
        while (rows.size < query.limit) {
            val (pageRows, hasMore) = rows(query, tagIds, page)
            rows += pageRows
            if (!hasMore || pageRows.isEmpty()) break
            page++
        }
        val unique = rows.distinctBy { it.appId }.take(query.limit)
        val prices = runSuspendCatching {
            unique.chunked(PRICE_BATCH).flatMap { chunk -> store.prices(chunk.map { it.appId }, query.country).toList() }.toMap()
        }
            .onFailure { log("Ask prices failed: ${it.message}") }
            .getOrNull()
            .orEmpty()
        log("Ask '${query.tags}' ${query.features} '${query.text}': ${unique.size} rows")
        return with(store) { unique.map { row -> row.toPick(prices[row.appId], row.tagIds.mapNotNull(names::get)) } }
    }

    override suspend fun tagsOf(title: String): List<String> {
        val response = client.get("$baseUrl/api/storesearch/") {
            parameter("term", title)
            parameter("cc", "US")
            parameter("l", "english")
        }
        if (!response.status.isSuccess()) throw DealsException("Steam search HTTP ${response.status.value}")
        val items = TrackerJson.decodeFromString(StoreSearchDto.serializer(), response.bodyAsText()).items
            .filter { it.type == null || it.type == "app" }
        val key = TitleKey.of(title)
        val app = items.firstOrNull { TitleKey.of(it.name) == key } ?: items.firstOrNull() ?: return emptyList()
        return spy.tags(app.id).entries.sortedByDescending { it.value }.map { it.key }.take(TAGS_OF)
    }

    /**
     * The `infinite` HTML mode carries tags and review scores; the plain `json` mode has only names
     * but is what the Deals screen falls back to on devices where the HTML mode fails, so Ask does too.
     */
    private suspend fun rows(query: FinderQuery, tagIds: List<Int>, page: Int): Pair<List<SteamStoreDeals.Row>, Boolean> =
        runSuspendCatching { htmlRows(query, tagIds, page) }
            .onFailure { log("Ask search rows failed, trying json: ${it.message}") }
            .getOrNull()
            ?: jsonRows(query, tagIds, page)

    private suspend fun htmlRows(query: FinderQuery, tagIds: List<Int>, page: Int): Pair<List<SteamStoreDeals.Row>, Boolean> {
        val response = client.get("$baseUrl/search/results/") {
            searchParameters(query, tagIds, page)
            parameter("infinite", 1)
        }
        if (!response.status.isSuccess()) throw DealsException("Steam search HTTP ${response.status.value}")
        val body = TrackerJson.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: throw DealsException("Steam search: not JSON")
        val html = body.string("results_html") ?: throw DealsException("Steam search: no results_html")
        val total = body.string("total_count")?.toIntOrNull() ?: 0
        val rows = SteamStoreDeals.parseRows(html)
        if (rows.isEmpty() && total > 0) throw DealsException("Steam search: $total results but no parsable rows")
        return rows to ((page + 1) * SteamStoreDeals.PAGE_SIZE < total)
    }

    private suspend fun jsonRows(query: FinderQuery, tagIds: List<Int>, page: Int): Pair<List<SteamStoreDeals.Row>, Boolean> {
        val response = client.get("$baseUrl/search/results/") {
            searchParameters(query, tagIds, page)
            parameter("json", 1)
        }
        if (!response.status.isSuccess()) throw DealsException("Steam search (json) HTTP ${response.status.value}")
        val items = TrackerJson.decodeFromString(SteamSearchDto.serializer(), response.bodyAsText()).items
        val rows = items.mapNotNull { item ->
            LOGO_APP.find(item.logo.orEmpty())?.groupValues?.get(1)?.toLongOrNull()?.let { SteamStoreDeals.Row(it, item.name) }
        }
        return rows to (items.size >= SteamStoreDeals.PAGE_SIZE)
    }

    private fun HttpRequestBuilder.searchParameters(query: FinderQuery, tagIds: List<Int>, page: Int) {
        if (tagIds.isNotEmpty()) parameter("tags", tagIds.joinToString(","))
        val categories = query.features.mapNotNull(::steamCategory)
        if (categories.isNotEmpty()) parameter("category3", categories.joinToString(","))
        if (query.text.isNotBlank()) parameter("term", query.text)
        if (query.onSale) parameter("specials", 1)
        if (query.free) parameter("maxprice", "free")
        parameter("category1", SteamStoreDeals.GAMES_CATEGORY)
        parameter("start", page * SteamStoreDeals.PAGE_SIZE)
        parameter("count", SteamStoreDeals.PAGE_SIZE)
        parameter("cc", query.country)
        parameter("l", "english")
    }

    internal companion object {
        private const val PRICE_BATCH = 50
        private const val TAGS_OF = 5
        private val LOGO_APP = Regex("/apps/(\\d+)/")

        /** Store search `category3` ids; the play modes Steam has no category for are left to tags. */
        fun steamCategory(feature: PlayFeature): Int? = Categories[feature]

        private val Categories = mapOf(
            PlayFeature.SPLIT_SCREEN_COOP to 39,
            PlayFeature.SHARED_SCREEN to 24,
            PlayFeature.LOCAL_PVP to 37,
            PlayFeature.ONLINE_COOP to 38,
            PlayFeature.COOP to 9,
            PlayFeature.PVP to 49,
            PlayFeature.SINGLE_PLAYER to 2,
            PlayFeature.REMOTE_PLAY_TOGETHER to 44,
            PlayFeature.CONTROLLER to 28,
        )
    }
}
