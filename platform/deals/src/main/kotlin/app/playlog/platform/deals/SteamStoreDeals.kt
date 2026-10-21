package app.playlog.platform.deals

import app.playlog.core.model.DealPick
import app.playlog.core.model.Platform
import app.playlog.core.data.StoreDealsSource
import app.playlog.core.model.StoreDealsPage
import app.playlog.core.network.TrackerJson
import app.playlog.platform.deals.JsonWalk.obj
import app.playlog.platform.deals.JsonWalk.string
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The Steam "Specials" search in any region: the search's JSON mode gives names and appids (in the
 * capsule URL), then one `appdetails?filters=price_overview` call prices the whole page.
 */
class SteamStoreDeals(
    private val client: HttpClient,
    private val baseUrl: String = "https://store.steampowered.com",
) : StoreDealsSource {
    override val platform = Platform.STEAM
    override val countries = StoreRegions.STEAM

    override suspend fun page(country: String, page: Int): StoreDealsPage {
        val response = client.get("$baseUrl/search/results/") {
            parameter("specials", 1)
            parameter("json", 1)
            parameter("category1", GAMES_CATEGORY)
            parameter("start", page * PAGE_SIZE)
            parameter("count", PAGE_SIZE)
            parameter("cc", country)
            parameter("l", "english")
        }
        if (!response.status.isSuccess()) throw DealsException("Steam search HTTP ${response.status.value}")
        val items = TrackerJson.decodeFromString(SteamSearchDto.serializer(), response.bodyAsText()).items
        val apps = items.mapNotNull { item -> APP_ID.find(item.logo.orEmpty())?.groupValues?.get(1)?.toLongOrNull()?.let { it to item.name } }
        val prices = prices(apps.map { it.first }, country)
        val deals = apps.mapNotNull { (appId, name) ->
            val price = prices[appId] ?: return@mapNotNull null
            if (price.discount <= 0) return@mapNotNull null
            DealPick(
                platform = Platform.STEAM,
                storeId = appId.toString(),
                title = name,
                headerUrl = steamHeader(appId),
                storeUrl = DealPick.steamStoreUrl(appId),
                discountPercent = price.discount,
                priceText = price.final,
                originalPriceText = price.initial,
            )
        }
        return StoreDealsPage(deals, hasMore = items.size >= PAGE_SIZE)
    }

    private data class Price(val discount: Int, val final: String, val initial: String?)

    private suspend fun prices(appIds: List<Long>, country: String): Map<Long, Price> {
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
            id.toLongOrNull()?.let { it to Price(discount, final, price.string("initial_formatted")) }
        }.toMap()
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val GAMES_CATEGORY = 998
        val APP_ID = Regex("/apps/(\\d+)/")
    }
}

@kotlinx.serialization.Serializable
internal data class SteamSearchDto(val items: List<SteamSearchItemDto> = emptyList())

@kotlinx.serialization.Serializable
internal data class SteamSearchItemDto(val name: String = "", val logo: String? = null)
