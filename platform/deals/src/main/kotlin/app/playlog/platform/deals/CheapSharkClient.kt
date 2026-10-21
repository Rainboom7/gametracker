package app.playlog.platform.deals

import app.playlog.core.network.TrackerJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.builtins.ListSerializer
import kotlin.math.roundToInt

/** A discounted Steam game from any deals feed, before similarity scoring. */
data class DealCandidate(
    val appId: Long,
    val title: String,
    val discountPercent: Int,
    val headerUrl: String? = null,
    val reviewPercent: Int? = null,
    /** CheapShark's 0..10 "how good is this deal" score. */
    val dealRating: Double? = null,
    /** USD prices, used when the regional store gives none. */
    val usdSalePrice: String? = null,
    val usdNormalPrice: String? = null,
)

/** Keyless CheapShark API, Steam deals only (store 1). Docs: apidocs.cheapshark.com. */
class CheapSharkClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://www.cheapshark.com",
) {
    suspend fun steamDeals(page: Int, pageSize: Int = PAGE_SIZE): List<DealCandidate> {
        val response = client.get("$baseUrl/api/1.0/deals") {
            parameter("storeID", STEAM_STORE_ID)
            parameter("onSale", 1)
            parameter("sortBy", "Deal Rating")
            parameter("pageSize", pageSize)
            parameter("pageNumber", page)
            parameter("steamRating", MIN_STEAM_RATING)
        }
        if (!response.status.isSuccess()) throw DealsException("CheapShark HTTP ${response.status.value}")
        return TrackerJson.decodeFromString(ListSerializer(CheapSharkDealDto.serializer()), response.bodyAsText()).mapNotNull { it.toCandidate() }
    }

    private fun CheapSharkDealDto.toCandidate(): DealCandidate? {
        val appId = steamAppID?.toLongOrNull() ?: return null
        return DealCandidate(
            appId = appId,
            title = title,
            discountPercent = savings?.toDoubleOrNull()?.roundToInt() ?: 0,
            headerUrl = steamHeader(appId),
            reviewPercent = steamRatingPercent?.toIntOrNull()?.takeIf { it > 0 },
            dealRating = dealRating?.toDoubleOrNull(),
            usdSalePrice = salePrice?.let { "$$it" },
            usdNormalPrice = normalPrice?.let { "$$it" },
        )
    }

    private companion object {
        const val STEAM_STORE_ID = 1
        const val PAGE_SIZE = 60
        const val MIN_STEAM_RATING = 70
    }
}

internal fun steamHeader(appId: Long) = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg"
