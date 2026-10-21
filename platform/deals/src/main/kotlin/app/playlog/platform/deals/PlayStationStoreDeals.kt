package app.playlog.platform.deals

import app.playlog.core.common.runSuspendCatching
import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import app.playlog.core.data.StoreDealsSource
import app.playlog.core.model.StoreDealsPage
import app.playlog.core.network.TrackerJson
import app.playlog.platform.deals.JsonWalk.array
import app.playlog.platform.deals.JsonWalk.obj
import app.playlog.platform.deals.JsonWalk.string
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/**
 * PlayStation Store "All Deals" category, reverse engineered from store.playstation.com:
 * 1. the web store's GraphQL persisted query `categoryGridRetrieve` (fast, JSON), and when Sony
 *    rotates its hash,
 * 2. the server-rendered category page, whose `__NEXT_DATA__` embeds the same Apollo cache.
 * Products are recognised by shape (`name` + `price.discountedPrice`), not by schema, so either
 * source and small schema changes parse the same way.
 */
class PlayStationStoreDeals(
    private val client: HttpClient,
    private val graphQlUrl: String = "https://web.np.playstation.com/api/graphql/v1/op",
    private val storeUrl: String = "https://store.playstation.com",
    private val categoryId: String = DEALS_CATEGORY,
    private val queryHash: String = CATEGORY_GRID_HASH,
) : StoreDealsSource {
    override val platform = Platform.PLAYSTATION
    override val countries = StoreRegions.PLAYSTATION.keys.toList()

    override suspend fun page(country: String, page: Int): StoreDealsPage {
        val locale = StoreRegions.PLAYSTATION[country] ?: throw DealsException("No PlayStation Store for $country")
        val viaApi = runSuspendCatching { graphQl(locale, page) }.getOrNull()
        if (viaApi != null && viaApi.deals.isNotEmpty()) return viaApi
        return html(locale, page)
    }

    private suspend fun graphQl(locale: String, page: Int): StoreDealsPage? {
        val variables = """{"id":"$categoryId","pageArgs":{"size":$PAGE_SIZE,"offset":${page * PAGE_SIZE}},""" +
            """"sortBy":{"name":"sales30","isAscending":false},"filterBy":[],"facetOptions":[]}"""
        val response = client.get(graphQlUrl) {
            parameter("operationName", "categoryGridRetrieve")
            parameter("variables", variables)
            parameter("extensions", """{"persistedQuery":{"version":1,"sha256Hash":"$queryHash"}}""")
            header("x-psn-store-locale-override", locale.uppercaseRegion())
            header("apollo-require-preflight", "true")
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.UserAgent, USER_AGENT)
        }
        if (!response.status.isSuccess()) return null
        val root = TrackerJson.parseToJsonElement(response.bodyAsText())
        val deals = products(root, locale)
        val isLast = JsonWalk.objects(root).firstNotNullOfOrNull { it.obj("pageInfo") }?.get("isLast")?.jsonPrimitive?.booleanOrNull
        return StoreDealsPage(deals, hasMore = isLast == false || (isLast == null && deals.size >= PAGE_SIZE / 2))
    }

    private suspend fun html(locale: String, page: Int): StoreDealsPage {
        val response = client.get("$storeUrl/$locale/category/$categoryId/${page + 1}") {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.AcceptLanguage, locale)
        }
        if (!response.status.isSuccess()) throw DealsException("PlayStation Store HTTP ${response.status.value}")
        val json = NEXT_DATA.find(response.bodyAsText())?.groupValues?.get(1)
            ?: throw DealsException("PlayStation Store page has no __NEXT_DATA__")
        val deals = products(TrackerJson.parseToJsonElement(json), locale)
        return StoreDealsPage(deals, hasMore = deals.size >= PAGE_SIZE / 2)
    }

    internal fun products(root: JsonElement, locale: String): List<DealPick> = JsonWalk.objects(root)
        .mapNotNull { it.toDeal(locale) }
        .distinctBy { it.storeId }
        .toList()

    private fun JsonObject.toDeal(locale: String): DealPick? {
        val id = string("id") ?: return null
        val name = string("name") ?: return null
        val price = obj("price")?.takeIf { it.isPublicSale() } ?: return null
        val discounted = price.string("discountedPrice") ?: return null
        val discount = JsonWalk.percent(price.string("discountText")) ?: return null
        val images = media()
        val header = HEADER_ROLES.firstNotNullOfOrNull { role -> images.firstOrNull { it.first == role }?.second }
            ?: images.firstOrNull()?.second ?: return null
        return DealPick(
            platform = Platform.PLAYSTATION,
            storeId = id,
            title = name,
            headerUrl = header,
            storeUrl = "$storeUrl/$locale/product/$id",
            screenshots = images.filter { it.first == "SCREENSHOT" }.map { it.second }.take(MAX_SCREENSHOTS),
            discountPercent = discount,
            priceText = discounted,
            originalPriceText = price.string("basePrice")?.takeIf { it != discounted },
            endsAt = price.endTime(),
        )
    }

    /** PS Plus-only prices are not a sale for everyone. */
    private fun JsonObject.isPublicSale(): Boolean =
        string("upsellServiceBranding") == null && this["upsellServiceBranding"] == null &&
            this["isTiedToSubscription"]?.jsonPrimitive?.booleanOrNull != true

    private fun JsonObject.endTime(): Instant? =
        this["endTime"]?.jsonPrimitive?.let { it.longOrNull ?: it.content.toLongOrNull() }?.let(Instant::ofEpochMilli)

    private fun JsonObject.media(): List<Pair<String, String>> = array("media").orEmpty().mapNotNull { e ->
        val m = e as? JsonObject ?: return@mapNotNull null
        m.string("url")?.let { (m.string("role") ?: "") to it }
    }

    private fun String.uppercaseRegion(): String {
        val dash = lastIndexOf('-')
        return if (dash < 0) this else substring(0, dash) + "-" + substring(dash + 1).uppercase()
    }

    internal companion object {
        /** "All Deals" on every storefront. */
        const val DEALS_CATEGORY = "3f772501-f6f8-49b7-abac-874a88ca4897"
        const val CATEGORY_GRID_HASH = "9845afc0dbaab4965f6563fffc703f588c8e76792000e8610843b8d3ee9c4c09"
        const val PAGE_SIZE = 24
        const val MAX_SCREENSHOTS = 6
        private val HEADER_ROLES = listOf("GAMEHUB_COVER_ART", "BACKGROUND", "MASTER")
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"
        private val NEXT_DATA = Regex("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
    }
}
