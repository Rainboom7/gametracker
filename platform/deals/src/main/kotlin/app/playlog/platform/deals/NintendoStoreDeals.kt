package app.playlog.platform.deals

import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import app.playlog.core.data.StoreDealsSource
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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Nintendo eShop sales. The catalogue differs by region (and so do the nsuids):
 * - Europe, UK, Oceania, South Africa: the public Solr search behind nintendo.com/en-gb.
 * - Americas: the public Algolia index behind nintendo.com/us (search-only key from the site).
 * Either way the shown price comes from `api.ec.nintendo.com/v1/price`, the eShop's own price
 * service, in the user's country; titles that are not discounted there are dropped.
 */
class NintendoStoreDeals(
    private val client: HttpClient,
    private val europeSearchUrl: String = "https://searching.nintendo-europe.com",
    private val algoliaUrl: String = "https://$ALGOLIA_APP-dsn.algolia.net",
    private val priceUrl: String = "https://api.ec.nintendo.com/v1/price",
    private val log: (String) -> Unit = {},
) : StoreDealsSource {
    override val platform = Platform.NINTENDO
    override val countries = StoreRegions.NINTENDO

    private data class Listing(
        val nsuid: String,
        val title: String,
        val image: String?,
        val description: String?,
        val genres: List<String> = emptyList(),
    )

    override suspend fun page(country: String, page: Int): StoreDealsPage {
        val (listings, hasMore) = catalogue(country, page, query = null)
        val deals = priced(country, listings).filter { it.isOnSale }
        log("Nintendo $country p$page: ${listings.size} listings, ${deals.size} deals")
        return StoreDealsPage(deals, hasMore)
    }

    override suspend fun quotes(country: String, ids: List<String>): Map<String, StoreQuote> =
        prices(country, ids).mapValues { (id, price) -> StoreQuote(storeUrl = storeUrl(id, country), price = price) }

    override suspend fun search(country: String, query: String): List<DealPick> {
        if (query.isBlank()) return emptyList()
        return priced(country, catalogue(country, 0, query).first, keepUnpriced = true)
    }

    private suspend fun catalogue(country: String, page: Int, query: String?): Pair<List<Listing>, Boolean> = when (country) {
        in StoreRegions.NINTENDO_EUROPE -> europe(StoreRegions.NINTENDO_EUROPE.getValue(country), page, query)
        in StoreRegions.NINTENDO_AMERICA -> america(StoreRegions.NINTENDO_AMERICA.getValue(country), page, query)
        else -> throw DealsException("No eShop catalogue for $country")
    }

    private suspend fun priced(country: String, listings: List<Listing>, keepUnpriced: Boolean = false): List<DealPick> {
        val prices = prices(country, listings.map { it.nsuid })
        return listings.mapNotNull { l ->
            val p = prices[l.nsuid]
            if (p == null && !keepUnpriced) return@mapNotNull null
            DealPick(
                platform = Platform.NINTENDO,
                storeId = l.nsuid,
                title = l.title,
                headerUrl = l.image ?: return@mapNotNull null,
                storeUrl = storeUrl(l.nsuid, country),
                shortDescription = l.description,
                discountPercent = p?.discountPercent ?: 0,
                priceText = p?.priceText.orEmpty(),
                originalPriceText = p?.originalPriceText,
                endsAt = p?.endsAt,
                priceValue = p?.priceValue,
                tags = l.genres,
            )
        }
    }

    private fun storeUrl(nsuid: String, country: String) = "https://ec.nintendo.com/apps/$nsuid/$country"

    private suspend fun europe(language: String, page: Int, query: String?): Pair<List<Listing>, Boolean> {
        val response = client.get("$europeSearchUrl/$language/select") {
            parameter("q", query?.let(::solrTerm) ?: "*")
            parameter("fq", if (query == null) "type:GAME AND price_has_discount_b:true" else "type:GAME")
            if (query == null) parameter("sort", "price_discount_percentage_f desc")
            parameter("rows", PAGE_SIZE)
            parameter("start", page * PAGE_SIZE)
            parameter("wt", "json")
        }
        if (!response.status.isSuccess()) throw DealsException("Nintendo search HTTP ${response.status.value}")
        val body = TrackerJson.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: throw DealsException("Nintendo search: not JSON")
        val docs = body.obj("response")?.array("docs").orEmpty().mapNotNull { it as? JsonObject }
        val listings = docs.mapNotNull { d ->
            val nsuid = (d.array("nsuid_txt")?.firstOrNull() as? JsonPrimitive)?.content ?: return@mapNotNull null
            val image = d.string("image_url_h2x1_s") ?: d.string("image_url_sq_s") ?: d.string("image_url")
            val genres = d.array("pretty_game_categories_txt").orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
            Listing(nsuid, d.string("title") ?: return@mapNotNull null, image?.let(::https), d.string("excerpt"), genres)
        }
        val total = body.obj("response")?.string("numFound")?.toIntOrNull() ?: 0
        return listings to ((page + 1) * PAGE_SIZE < total)
    }

    private suspend fun america(index: String, page: Int, query: String?): Pair<List<Listing>, Boolean> {
        val request = kotlinx.serialization.json.buildJsonObject {
            put("query", JsonPrimitive(query.orEmpty()))
            put("hitsPerPage", JsonPrimitive(PAGE_SIZE))
            put("page", JsonPrimitive(page))
            if (query == null) put("filters", JsonPrimitive("topLevelFilters:\"Deals\""))
        }
        val response = client.post("$algoliaUrl/1/indexes/$index/query") {
            header("X-Algolia-Application-Id", ALGOLIA_APP)
            header("X-Algolia-API-Key", ALGOLIA_KEY)
            contentType(ContentType.Application.Json)
            setBody(request.toString())
        }
        if (!response.status.isSuccess()) throw DealsException("Nintendo Algolia HTTP ${response.status.value}")
        val body = TrackerJson.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: throw DealsException("Nintendo Algolia: not JSON")
        val hits = body.array("hits").orEmpty().mapNotNull { it as? JsonObject }
        val listings = hits.mapNotNull { h ->
            val nsuid = h.string("nsuid") ?: return@mapNotNull null
            val image = h.string("horizontalHeaderImage") ?: h.string("productImage") ?: h.string("boxart")
            val genres = h.array("genres").orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
            Listing(nsuid, h.string("title") ?: return@mapNotNull null, image?.let(::nintendoAsset), h.string("description"), genres)
        }
        val pages = body.string("nbPages")?.toIntOrNull() ?: 0
        return listings to (page + 1 < pages)
    }

    /** Every priced id; a missing `discount_price` means full price. */
    private suspend fun prices(country: String, nsuids: List<String>): Map<String, StorePrice> = nsuids.chunked(PRICE_BATCH).flatMap { chunk ->
        val response = client.get(priceUrl) {
            parameter("country", country)
            parameter("lang", "en")
            parameter("ids", chunk.joinToString(","))
        }
        if (!response.status.isSuccess()) throw DealsException("Nintendo price HTTP ${response.status.value}")
        val body = TrackerJson.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return@flatMap emptyList()
        body.array("prices").orEmpty().mapNotNull { e ->
            val p = e as? JsonObject ?: return@mapNotNull null
            val id = p.string("title_id") ?: return@mapNotNull null
            val regular = p.obj("regular_price") ?: return@mapNotNull null
            val regularText = regular.string("amount") ?: return@mapNotNull null
            val regularValue = regular.string("raw_value")?.toDoubleOrNull()
            id to (p.obj("discount_price")?.let { sale(it, regularText, regularValue) } ?: StorePrice(regularText, priceValue = regularValue))
        }
    }.toMap()

    /** Null unless the discount price really is below the regular one. */
    private fun sale(sale: JsonObject, regularText: String, regularValue: Double?): StorePrice? {
        val saleValue = sale.string("raw_value")?.toDoubleOrNull() ?: return null
        val saleText = sale.string("amount") ?: return null
        if (regularValue == null || regularValue <= 0 || saleValue >= regularValue) return null
        return StorePrice(
            priceText = saleText,
            originalPriceText = regularText,
            discountPercent = Math.round((1 - saleValue / regularValue) * PERCENT).toInt(),
            priceValue = saleValue,
            endsAt = sale.string("end_datetime")?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() },
        )
    }

    /** A quoted Solr phrase on the title; quotes inside are dropped rather than escaped. */
    private fun solrTerm(query: String) = "title:\"" + query.replace("\"", " ").trim() + "\""

    private fun https(url: String) = if (url.startsWith("//")) "https:$url" else url

    private fun nintendoAsset(id: String) =
        if (id.startsWith("http")) id else "https://assets.nintendo.com/image/upload/ar_16:9,c_lpad,w_656/b_white/f_auto/q_auto/$id"

    private companion object {
        const val PAGE_SIZE = 30
        const val PRICE_BATCH = 50
        const val PERCENT = 100
        const val ALGOLIA_APP = "U3B6GR4UA3"

        /** Search-only key embedded in nintendo.com's own pages. */
        const val ALGOLIA_KEY = "a29c6927638bfd8cee23993e51e721c9"
    }
}
