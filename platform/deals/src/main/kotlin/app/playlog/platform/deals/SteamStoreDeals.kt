package app.playlog.platform.deals

import app.playlog.core.common.runSuspendCatching
import app.playlog.core.data.StoreDealsSource
import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import app.playlog.core.model.StoreDealsPage
import app.playlog.core.model.StorePrice
import app.playlog.core.model.StoreQuote
import app.playlog.core.network.TrackerJson
import app.playlog.platform.deals.JsonWalk.array
import app.playlog.platform.deals.JsonWalk.obj
import app.playlog.platform.deals.JsonWalk.string
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/**
 * The Steam "Specials" search in any region. The search's HTML rows carry appids, user tag ids and
 * the review summary; one `appdetails?filters=price_overview` call then prices the whole page in
 * the region's currency. If the row markup changes, the plain JSON search (names only) still works.
 */
class SteamStoreDeals(
    private val client: HttpClient,
    private val baseUrl: String = "https://store.steampowered.com",
    private val apiUrl: String = "https://api.steampowered.com",
    private val log: (String) -> Unit = {},
) : StoreDealsSource {
    override val platform = Platform.STEAM
    override val countries = StoreRegions.STEAM

    internal data class Row(
        val appId: Long,
        val name: String,
        val tagIds: List<Int> = emptyList(),
        val review: Int? = null,
        /** The row's discount badge, when the markup has one. */
        val discount: Int? = null,
        /** Release column as shown: "24 Oct, 2026", "Q1 2027", "Coming soon". */
        val released: String? = null,
        /** The row's own small capsule: the real (hashed) asset path, unlike one built from the id. */
        val capsule: String? = null,
    )

    private val tagLock = Mutex()
    private var tagNames: Map<Int, String>? = null

    override suspend fun page(country: String, page: Int): StoreDealsPage {
        val (rows, hasMore) = runSuspendCatching { htmlRows(country, page) }
            .onFailure { log("Steam search rows failed: ${it.message}") }
            .getOrNull()
            ?.takeIf { it.first.isNotEmpty() }
            ?: jsonRows(country, page)
        val prices = prices(rows.map { it.appId }, country)
        val names = if (rows.any { it.tagIds.isNotEmpty() }) tagNames() else emptyMap()
        val deals = rows.mapNotNull { row ->
            val price = prices[row.appId]?.takeIf { it.discountPercent > 0 } ?: return@mapNotNull null
            row.toPick(price, row.tagIds.mapNotNull(names::get))
        }
        log("Steam $country p$page: ${rows.size} rows, ${deals.size} deals")
        return StoreDealsPage(deals, hasMore)
    }

    /** One call per 100 apps; `IStoreBrowseService` names and prices apps that are not on sale too. */
    override suspend fun quotes(country: String, ids: List<String>): Map<String, StoreQuote> {
        val appIds = ids.mapNotNull(String::toLongOrNull)
        return appIds.chunked(QUOTE_BATCH).flatMap { chunk ->
            runSuspendCatching { storeItems(chunk, country) }
                .onFailure { log("Steam GetItems failed: ${it.message}") }
                .getOrNull()
                ?: prices(chunk, country).map { (id, price) -> id.toString() to StoreQuote(storeUrl = DealPick.steamStoreUrl(id), price = price) }
        }.toMap()
    }

    override suspend fun search(country: String, query: String): List<DealPick> {
        if (query.isBlank()) return emptyList()
        val response = client.get("$baseUrl/api/storesearch/") {
            parameter("term", query)
            parameter("cc", country)
            parameter("l", "english")
        }
        if (!response.status.isSuccess()) throw DealsException("Steam store search HTTP ${response.status.value}")
        val apps = TrackerJson.decodeFromString(StoreSearchDto.serializer(), response.bodyAsText()).items
            .filter { it.type == null || it.type == "app" }
            .map { Row(it.id, it.name) }
        val prices = prices(apps.map { it.appId }, country)
        return apps.map { row -> row.toPick(prices[row.appId], emptyList()) }
    }

    internal fun Row.toPick(price: StorePrice?, tags: List<String>) = DealPick(
        platform = Platform.STEAM,
        storeId = appId.toString(),
        title = name,
        headerUrl = steamHeader(appId),
        storeUrl = DealPick.steamStoreUrl(appId),
        discountPercent = price?.discountPercent ?: 0,
        priceText = price?.priceText.orEmpty(),
        originalPriceText = price?.originalPriceText,
        reviewPercent = review,
        priceValue = price?.priceValue,
        tags = tags,
    )

    private suspend fun htmlRows(country: String, page: Int): Pair<List<Row>, Boolean> {
        val response = client.get("$baseUrl/search/results/") {
            searchParameters(country, page)
            parameter("infinite", 1)
        }
        if (!response.status.isSuccess()) throw DealsException("Steam search HTTP ${response.status.value}")
        val body = TrackerJson.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: throw DealsException("Steam search: not JSON")
        val html = body.string("results_html").orEmpty()
        val total = body.string("total_count")?.toIntOrNull() ?: 0
        return parseRows(html) to ((page + 1) * PAGE_SIZE < total)
    }

    private suspend fun jsonRows(country: String, page: Int): Pair<List<Row>, Boolean> {
        val response = client.get("$baseUrl/search/results/") {
            searchParameters(country, page)
            parameter("json", 1)
        }
        if (!response.status.isSuccess()) throw DealsException("Steam search HTTP ${response.status.value}")
        val items = TrackerJson.decodeFromString(SteamSearchDto.serializer(), response.bodyAsText()).items
        val rows = items.mapNotNull { item -> APP_ID.find(item.logo.orEmpty())?.groupValues?.get(1)?.toLongOrNull()?.let { Row(it, item.name) } }
        return rows to (items.size >= PAGE_SIZE)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.searchParameters(country: String, page: Int) {
        parameter("specials", 1)
        parameter("category1", GAMES_CATEGORY)
        parameter("start", page * PAGE_SIZE)
        parameter("count", PAGE_SIZE)
        parameter("cc", country)
        parameter("l", "english")
    }

    internal suspend fun tagNames(): Map<Int, String> = tagLock.withLock {
        tagNames ?: runSuspendCatching {
            val response = client.get("$baseUrl/tagdata/populartags/english")
            if (!response.status.isSuccess()) throw DealsException("Steam tags HTTP ${response.status.value}")
            (TrackerJson.parseToJsonElement(response.bodyAsText()) as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val id = o["tagid"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                o.string("name")?.let { id to it }
            }.toMap()
        }.onFailure { log("Steam tag names failed: ${it.message}") }.getOrNull()?.also { tagNames = it }.orEmpty()
    }

    internal suspend fun prices(appIds: List<Long>, country: String): Map<Long, StorePrice> {
        if (appIds.isEmpty()) return emptyMap()
        val response = client.get("$baseUrl/api/appdetails") {
            parameter("appids", appIds.joinToString(","))
            parameter("filters", "price_overview")
            parameter("cc", country)
        }
        if (!response.status.isSuccess()) throw DealsException("Steam prices HTTP ${response.status.value}")
        val root = TrackerJson.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return emptyMap()
        // Free or unpriced apps answer `"data": []`, so this walks objects instead of decoding a schema.
        return root.mapNotNull { (id, envelope) ->
            val price = (envelope as? JsonObject)?.obj("data")?.obj("price_overview") ?: return@mapNotNull null
            val final = price.string("final_formatted") ?: return@mapNotNull null
            val discount = price["discount_percent"]?.jsonPrimitive?.intOrNull ?: 0
            val cents = price["final"]?.jsonPrimitive?.longOrNull
            id.toLongOrNull()?.let {
                it to StorePrice(final, price.string("initial_formatted")?.takeIf { discount > 0 }, discount, cents?.let { c -> c / CENTS })
            }
        }.toMap()
    }

    private suspend fun storeItems(appIds: List<Long>, country: String): List<Pair<String, StoreQuote>> {
        val ids = appIds.joinToString(",") { """{"appid":$it}""" }
        val response = client.get("$apiUrl/IStoreBrowseService/GetItems/v1/") {
            parameter(
                "input_json",
                """{"ids":[$ids],"context":{"language":"english","country_code":"$country","steam_realm":1},""" +
                    """"data_request":{"include_assets":true,"include_release":true}}""",
            )
        }
        if (!response.status.isSuccess()) throw DealsException("Steam GetItems HTTP ${response.status.value}")
        val items = (TrackerJson.parseToJsonElement(response.bodyAsText()) as? JsonObject)?.obj("response")?.array("store_items")
            ?: throw DealsException("Steam GetItems: no store_items")
        return items.mapNotNull { e ->
            val item = e as? JsonObject ?: return@mapNotNull null
            val appId = item.string("appid")?.toLongOrNull() ?: item.string("id")?.toLongOrNull() ?: return@mapNotNull null
            val option = item.obj("best_purchase_option")
            val price = option?.string("formatted_final_price")?.let { final ->
                val discount = option["discount_pct"]?.jsonPrimitive?.intOrNull ?: 0
                StorePrice(
                    priceText = final,
                    originalPriceText = option.string("formatted_original_price")?.takeIf { discount > 0 },
                    discountPercent = discount,
                    priceValue = option.string("final_price_in_cents")?.toDoubleOrNull()?.div(CENTS),
                    endsAt = option.array("active_discounts")?.firstOrNull()?.let { it as? JsonObject }
                        ?.string("discount_end_date")?.toLongOrNull()?.let(Instant::ofEpochSecond),
                )
            }
            val release = item.obj("release")
            appId.toString() to StoreQuote(
                title = item.string("name"),
                headerUrl = steamHeader(appId),
                storeUrl = DealPick.steamStoreUrl(appId),
                price = price,
                comingSoon = release?.let { it["is_coming_soon"]?.jsonPrimitive?.booleanOrNull ?: false },
                releaseDate = release?.string("steam_release_date")?.toLongOrNull()?.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
            )
        }
    }

    internal companion object {
        const val PAGE_SIZE = 50
        private const val QUOTE_BATCH = 100
        const val GAMES_CATEGORY = 998
        private const val CENTS = 100.0
        private val APP_ID = Regex("/apps/(\\d+)/")
        private val ROW_APP = Regex("data-ds-appid=\"(\\d+)\"")
        private val ROW_TAGS = Regex("data-ds-tagids=\"\\[([\\d,]*)]\"")
        private val ROW_TITLE = Regex("<span class=\"title\">([^<]*)</span>")
        private val ROW_DISCOUNT = Regex("data-discount=\"(\\d{1,3})\"")
        private val ROW_RELEASED = Regex("search_released[^\"]*\">([^<]*)<")
        private val ROW_CAPSULE = Regex("search_capsule\"[^>]*>\\s*<img[^>]*?src=\"([^\"]+)\"")
        private val ROW_REVIEW = Regex("data-tooltip-html=\"[^\"]*?(\\d{1,3})%")

        /** Search result rows; bundles and packages (several appids) are skipped. */
        fun parseRows(html: String): List<Row> = html.split("<a ").mapNotNull { chunk ->
            if (!chunk.contains("search_result_row")) return@mapNotNull null
            val appId = ROW_APP.find(chunk)?.groupValues?.get(1)?.toLongOrNull() ?: return@mapNotNull null
            val title = ROW_TITLE.find(chunk)?.groupValues?.get(1)?.let(::unescape)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val tags = ROW_TAGS.find(chunk)?.groupValues?.get(1).orEmpty().split(',').mapNotNull(String::toIntOrNull)
            Row(
                appId = appId,
                name = title,
                tagIds = tags,
                review = ROW_REVIEW.find(chunk)?.groupValues?.get(1)?.toIntOrNull(),
                discount = ROW_DISCOUNT.find(chunk)?.groupValues?.get(1)?.toIntOrNull(),
                released = ROW_RELEASED.find(chunk)?.groupValues?.get(1)?.let(::unescape)?.takeIf(String::isNotBlank),
                capsule = ROW_CAPSULE.find(chunk)?.groupValues?.get(1)?.replace("capsule_sm_120", "header"),
            )
        }

        private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").trim()
    }
}

@kotlinx.serialization.Serializable
internal data class SteamSearchDto(val items: List<SteamSearchItemDto> = emptyList())

@kotlinx.serialization.Serializable
internal data class SteamSearchItemDto(val name: String = "", val logo: String? = null)
