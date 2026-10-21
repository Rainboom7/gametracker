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
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/**
 * PlayStation Store "All Deals" category, reverse engineered from store.playstation.com:
 * 1. the web store's GraphQL persisted query `categoryGridRetrieve` (fast, JSON). Sony rotates the
 *    query hash every few months, so several known hashes are tried and the one that answers is kept;
 * 2. the server-rendered category page, whose `__NEXT_DATA__` embeds the same Apollo cache.
 * Products are recognised by shape (`name` + `price`), not by schema, so either source and small
 * schema changes parse the same way. [log] gets one line per attempt for logcat diagnostics.
 */
class PlayStationStoreDeals(
    private val client: HttpClient,
    private val graphQlUrl: String = "https://web.np.playstation.com/api/graphql/v1/op",
    private val storeUrl: String = "https://store.playstation.com",
    private val categoryId: String = DEALS_CATEGORY,
    private val gridHashes: List<String> = CATEGORY_GRID_HASHES,
    private val log: (String) -> Unit = {},
) : StoreDealsSource {
    override val platform = Platform.PLAYSTATION
    override val countries = StoreRegions.PLAYSTATION.keys.toList()

    @Volatile private var workingHash: String? = null

    override suspend fun page(country: String, page: Int): StoreDealsPage {
        val locale = locale(country)
        val viaApi = runSuspendCatching { graphQl(locale, page) }
            .onFailure { log("PS grid query failed: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrNull()
        if (viaApi != null && viaApi.deals.isNotEmpty()) return viaApi
        return html(locale, page)
    }

    /** Wishlist prices: PlayStation has no anonymous price lookup by id, so the sale pages are scanned. */
    override suspend fun quotes(country: String, ids: List<String>): Map<String, StoreQuote> {
        val wanted = ids.toMutableSet()
        val found = HashMap<String, StoreQuote>()
        var page = 0
        while (wanted.isNotEmpty() && page < MAX_SCAN_PAGES) {
            val result = page(country, page)
            result.deals.filter { it.storeId in wanted }.forEach { d ->
                wanted -= d.storeId
                found[d.storeId] = StoreQuote(d.title, d.headerUrl, d.storeUrl, d.toPrice())
            }
            if (!result.hasMore) break
            page++
        }
        return found
    }

    override suspend fun search(country: String, query: String): List<DealPick> {
        if (query.isBlank()) return emptyList()
        val locale = locale(country)
        val response = get("$storeUrl/$locale/search/${query.encodeURLPathPart()}", locale)
        if (!response.status.isSuccess()) throw DealsException("PlayStation search HTTP ${response.status.value}")
        val json = NEXT_DATA.find(response.bodyAsText())?.groupValues?.get(1) ?: throw DealsException("PlayStation search has no __NEXT_DATA__")
        return products(TrackerJson.parseToJsonElement(json), locale, saleOnly = false).also { log("PS search '$query': ${it.size} products") }
    }

    private fun locale(country: String) = StoreRegions.PLAYSTATION[country] ?: throw DealsException("No PlayStation Store for $country")

    private suspend fun graphQl(locale: String, page: Int): StoreDealsPage? {
        val variables = """{"id":"$categoryId","pageArgs":{"size":$PAGE_SIZE,"offset":${page * PAGE_SIZE}},""" +
            """"sortBy":{"name":"sales30","isAscending":false},"filterBy":[],"facetOptions":[]}"""
        val hashes = listOfNotNull(workingHash) + gridHashes.filter { it != workingHash }
        for (hash in hashes) {
            val response = client.get(graphQlUrl) {
                parameter("operationName", "categoryGridRetrieve")
                parameter("variables", variables)
                parameter("extensions", """{"persistedQuery":{"version":1,"sha256Hash":"$hash"}}""")
                header("x-psn-store-locale-override", locale.uppercaseRegion())
                header("x-apollo-operation-name", "categoryGridRetrieve")
                header("apollo-require-preflight", "true")
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, USER_AGENT)
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                log("PS grid ${hash.take(HASH_PREVIEW)}: HTTP ${response.status.value} ${text.take(LOG_BODY)}")
                continue
            }
            val root = runCatching { TrackerJson.parseToJsonElement(text) }.getOrNull()
            val grid = (root as? JsonObject)?.obj("data")?.obj("categoryGridRetrieve")
            if (grid == null) {
                log("PS grid ${hash.take(HASH_PREVIEW)}: no grid, ${text.take(LOG_BODY)}")
                continue
            }
            workingHash = hash
            val deals = products(grid, locale, saleOnly = true)
            val isLast = grid.obj("pageInfo")?.get("isLast")?.jsonPrimitive?.booleanOrNull
            log("PS grid ${hash.take(HASH_PREVIEW)} $locale p$page: ${deals.size} deals, isLast=$isLast")
            return StoreDealsPage(deals, hasMore = isLast == false || (isLast == null && deals.size >= PAGE_SIZE / 2))
        }
        return null
    }

    private suspend fun html(locale: String, page: Int): StoreDealsPage {
        val response = get("$storeUrl/$locale/category/$categoryId/${page + 1}", locale)
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            log("PS page HTTP ${response.status.value}: ${body.take(LOG_BODY)}")
            throw DealsException("PlayStation Store HTTP ${response.status.value}")
        }
        val json = NEXT_DATA.find(body)?.groupValues?.get(1)
            ?: throw DealsException("PlayStation Store page has no __NEXT_DATA__").also { log("PS page without __NEXT_DATA__: ${body.take(LOG_BODY)}") }
        val deals = products(TrackerJson.parseToJsonElement(json), locale, saleOnly = true)
        log("PS page $locale p$page: ${deals.size} deals")
        return StoreDealsPage(deals, hasMore = deals.size >= PAGE_SIZE / 2)
    }

    /** The web store redirects between locales and to trailing-slash paths; the shared client does not follow redirects. */
    private suspend fun get(url: String, locale: String): HttpResponse {
        var target = url
        repeat(MAX_REDIRECTS) {
            val response = client.get(target) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.AcceptLanguage, locale)
                header(HttpHeaders.Accept, "text/html")
            }
            val location = response.headers[HttpHeaders.Location]
            if (response.status.value !in REDIRECTS || location == null) return response
            target = if (location.startsWith("/")) storeUrl + location else location
        }
        throw DealsException("PlayStation Store redirect loop")
    }

    internal fun products(root: JsonElement, locale: String, saleOnly: Boolean = true): List<DealPick> = JsonWalk.objects(root)
        .mapNotNull { it.toDeal(locale, saleOnly) }
        .distinctBy { it.storeId }
        .toList()

    private fun JsonObject.toDeal(locale: String, saleOnly: Boolean): DealPick? {
        val id = string("id") ?: return null
        val name = string("name") ?: return null
        val price = obj("price") ?: return null
        val discount = if (price.isPublicSale()) JsonWalk.percent(price.string("discountText")) ?: 0 else 0
        if (saleOnly && discount == 0) return null
        val base = price.string("basePrice")
        val shown = price.shownPrice(discount, base) ?: return null
        val images = media()
        val header = header(images) ?: return null
        return DealPick(
            platform = Platform.PLAYSTATION,
            storeId = id,
            title = name,
            headerUrl = header,
            storeUrl = "$storeUrl/$locale/product/$id",
            screenshots = images.filter { it.first == "SCREENSHOT" }.map { it.second }.take(MAX_SCREENSHOTS),
            discountPercent = discount,
            priceText = shown,
            originalPriceText = base?.takeIf { discount > 0 && it != shown },
            endsAt = if (discount > 0) price.endTime() else null,
            priceValue = PriceText.value(shown),
            tags = genres(),
        )
    }

    private fun header(images: List<Pair<String, String>>): String? =
        HEADER_ROLES.firstNotNullOfOrNull { role -> images.firstOrNull { it.first == role }?.second } ?: images.firstOrNull()?.second

    /** Prices that need PS Plus are not a sale for everyone; real listings carry `["NONE"]` here. */
    private fun JsonObject.shownPrice(discount: Int, base: String?): String? =
        (if (discount > 0) string("discountedPrice") else null) ?: base ?: string("discountedPrice")

    private fun JsonObject.isPublicSale(): Boolean {
        val branding = listOfNotNull(this["serviceBranding"], this["upsellServiceBranding"]).flatMap { it.strings() }
        return branding.none { it.contains("PLUS", ignoreCase = true) || it.contains("EA_ACCESS", ignoreCase = true) } &&
            this["isTiedToSubscription"]?.jsonPrimitive?.booleanOrNull != true
    }

    private fun JsonElement.strings(): List<String> = when (this) {
        is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.content }
        is JsonPrimitive -> listOf(content)
        else -> emptyList()
    }

    private fun JsonObject.endTime(): Instant? =
        this["endTime"]?.let { it as? JsonPrimitive }?.let { it.longOrNull ?: it.content.toLongOrNull() }?.let(Instant::ofEpochMilli)

    private fun JsonObject.media(): List<Pair<String, String>> = array("media").orEmpty().mapNotNull { e ->
        val m = e as? JsonObject ?: return@mapNotNull null
        m.string("url")?.let { (m.string("role") ?: "") to it }
    }

    private fun JsonObject.genres(): List<String> = (array("localizedGenres") ?: array("genres")).orEmpty().mapNotNull { g ->
        (g as? JsonObject)?.let { it.string("value") ?: it.string("name") } ?: (g as? JsonPrimitive)?.content
    }

    private fun DealPick.toPrice() = StorePrice(priceText, originalPriceText, discountPercent, priceValue, endsAt)

    private fun String.uppercaseRegion(): String {
        val dash = lastIndexOf('-')
        return if (dash < 0) this else substring(0, dash) + "-" + substring(dash + 1).uppercase()
    }

    internal companion object {
        /** "All Deals" on every storefront. */
        const val DEALS_CATEGORY = "3f772501-f6f8-49b7-abac-874a88ca4897"

        /** Newest first: the web store's current hash, then older ones Sony still answered recently. */
        val CATEGORY_GRID_HASHES = listOf(
            "88c0b9a1273c6d320c51cd73e390924e21ae28bf09f01cde8b84b1034b16cd03",
            "9845afc0dbaab4965f6563fffc703f588c8e76792000e8610843b8d3ee9c4c09",
            "4ce7d410a4db2c8b635a48c1dcec375906ff63b19dadd87e073f8fd0c0481d35",
        )
        const val PAGE_SIZE = 24
        const val MAX_SCREENSHOTS = 6
        const val MAX_SCAN_PAGES = 8
        private const val MAX_REDIRECTS = 4
        private const val HASH_PREVIEW = 8
        private const val LOG_BODY = 200
        private val REDIRECTS = 300..399
        private val HEADER_ROLES = listOf("GAMEHUB_COVER_ART", "BACKGROUND", "MASTER")
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"
        private val NEXT_DATA = Regex("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
    }
}
