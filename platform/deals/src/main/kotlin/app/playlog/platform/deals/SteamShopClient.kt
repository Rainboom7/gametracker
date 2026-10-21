package app.playlog.platform.deals

import app.playlog.core.network.TrackerJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Public, keyless Steam store endpoints in the user's region: [countryCode] picks the currency,
 * [language] the description and price format ("russian", "english").
 */
class SteamShopClient(
    private val client: HttpClient,
    private val countryCode: String,
    private val language: String,
    private val baseUrl: String = "https://store.steampowered.com",
) {
    /** Store page facts for a deal card; null when the app is not sold in the region. */
    data class ShopDetails(
        val title: String,
        val headerUrl: String?,
        val shortDescription: String?,
        val screenshots: List<String>,
        val discountPercent: Int,
        val priceText: String?,
        val originalPriceText: String?,
    )

    /** The front page "Specials" row. */
    suspend fun specials(): List<DealCandidate> {
        val response = client.get("$baseUrl/api/featuredcategories") {
            parameter("cc", countryCode)
            parameter("l", language)
        }
        if (!response.status.isSuccess()) throw DealsException("Steam featured HTTP ${response.status.value}")
        val dto = TrackerJson.decodeFromString(FeaturedCategoriesDto.serializer(), response.bodyAsText())
        return dto.specials?.items.orEmpty()
            .filter { it.discountPercent > 0 }
            .map { DealCandidate(it.id, it.name, it.discountPercent, it.headerImage ?: steamHeader(it.id)) }
    }

    /** Store search by name; used to place PlayStation and Nintendo titles on the Steam tag map. */
    suspend fun search(term: String): List<Pair<Long, String>> {
        val response = client.get("$baseUrl/api/storesearch/") {
            parameter("term", term)
            parameter("cc", countryCode)
            parameter("l", "english")
        }
        if (!response.status.isSuccess()) throw DealsException("Steam search HTTP ${response.status.value}")
        return TrackerJson.decodeFromString(StoreSearchDto.serializer(), response.bodyAsText()).items
            .filter { it.type == null || it.type == "app" }
            .map { it.id to it.name }
    }

    suspend fun details(appId: Long): ShopDetails? {
        val response = client.get("$baseUrl/api/appdetails") {
            parameter("appids", appId)
            parameter("cc", countryCode)
            parameter("l", language)
        }
        if (!response.status.isSuccess()) throw DealsException("Steam appdetails HTTP ${response.status.value}")
        val envelopes = TrackerJson.decodeFromString(MapSerializer(String.serializer(), ShopDetailsEnvelope.serializer()), response.bodyAsText())
        val data = envelopes[appId.toString()]?.takeIf { it.success }?.data ?: return null
        return ShopDetails(
            title = data.name.orEmpty(),
            headerUrl = data.headerImage,
            shortDescription = data.shortDescription?.let(::stripHtml)?.takeIf(String::isNotBlank),
            screenshots = data.screenshots.mapNotNull { it.full ?: it.thumbnail }.take(MAX_SCREENSHOTS),
            discountPercent = data.price?.discountPercent ?: 0,
            priceText = data.price?.finalFormatted,
            originalPriceText = data.price?.initialFormatted?.takeIf(String::isNotBlank),
        )
    }

    private companion object {
        const val MAX_SCREENSHOTS = 6
        val TAG = Regex("<[^>]+>")
        val ENTITIES = mapOf("&amp;" to "&", "&quot;" to "\"", "&#39;" to "'", "&lt;" to "<", "&gt;" to ">", "&nbsp;" to " ")

        fun stripHtml(html: String): String =
            ENTITIES.entries.fold(html.replace(TAG, "")) { s, (entity, char) -> s.replace(entity, char) }.trim()
    }
}
