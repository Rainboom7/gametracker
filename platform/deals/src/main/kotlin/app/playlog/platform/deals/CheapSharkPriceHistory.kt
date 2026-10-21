package app.playlog.platform.deals

import app.playlog.core.data.PriceHistory
import app.playlog.core.model.Platform
import app.playlog.core.model.PriceLow
import app.playlog.core.network.TrackerJson
import app.playlog.platform.deals.JsonWalk.obj
import app.playlog.platform.deals.JsonWalk.string
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Lowest price ever across PC stores, from CheapShark (keyless, USD only). Only Steam games can be
 * looked up: consoles have no free price history API.
 */
class CheapSharkPriceHistory(
    private val client: HttpClient,
    private val baseUrl: String = "https://www.cheapshark.com",
) : PriceHistory {
    private val cache = ConcurrentHashMap<String, Result<PriceLow?>>()

    override suspend fun lowest(platform: Platform, storeId: String): PriceLow? {
        if (platform != Platform.STEAM) return null
        cache[storeId]?.let { return it.getOrThrow() }
        val result = runCatching { fetch(storeId) }
        // Failures are not cached, so a later open can retry.
        if (result.isSuccess) cache[storeId] = result
        return result.getOrThrow()
    }

    private suspend fun fetch(appId: String): PriceLow? {
        val lookup = client.get("$baseUrl/api/1.0/games") { parameter("steamAppID", appId) }
        if (!lookup.status.isSuccess()) throw DealsException("CheapShark HTTP ${lookup.status.value}")
        val gameId = (TrackerJson.parseToJsonElement(lookup.bodyAsText()) as? JsonArray)
            ?.firstNotNullOfOrNull { (it as? JsonObject)?.string("gameID") } ?: return null
        val game = client.get("$baseUrl/api/1.0/games") { parameter("id", gameId) }
        if (!game.status.isSuccess()) throw DealsException("CheapShark HTTP ${game.status.value}")
        val lowest = (TrackerJson.parseToJsonElement(game.bodyAsText()) as? JsonObject)?.obj("cheapestPriceEver") ?: return null
        val price = lowest.string("price") ?: return null
        return PriceLow("$$price", lowest.string("date")?.toLongOrNull()?.let(Instant::ofEpochSecond), "CheapShark")
    }
}
