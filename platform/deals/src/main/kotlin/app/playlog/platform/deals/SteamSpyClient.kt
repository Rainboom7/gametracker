package app.playlog.platform.deals

import app.playlog.core.network.TrackerJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * SteamSpy user tags with vote counts (`{"Roguelike": 5123, "Action": 3010, …}`), the best free
 * signal for "games like this". SteamSpy asks for at most one request per second.
 */
class SteamSpyClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://steamspy.com",
    private val minIntervalMillis: Long = 1_000,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()
    private var lastRequestAt = 0L

    /** Tag → votes; empty when SteamSpy knows no tags for the app. */
    suspend fun tags(appId: Long): Map<String, Int> {
        val text = lock.withLock {
            val wait = lastRequestAt + minIntervalMillis - nowMillis()
            if (wait > 0) delay(wait)
            lastRequestAt = nowMillis()
            val response = client.get("$baseUrl/api.php") {
                parameter("request", "appdetails")
                parameter("appid", appId)
            }
            if (!response.status.isSuccess()) throw DealsException("SteamSpy HTTP ${response.status.value}")
            response.bodyAsText()
        }
        // An app without tags has `"tags": []` instead of an object.
        val tags = (TrackerJson.parseToJsonElement(text) as? JsonObject)?.get("tags") as? JsonObject ?: return emptyMap()
        return tags.mapNotNull { (name, votes) -> votes.jsonPrimitive.intOrNull?.takeIf { it > 0 }?.let { name to it } }.toMap()
    }
}
