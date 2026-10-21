package app.playlog.platform.nintendo

import app.playlog.core.model.Platform
import app.playlog.core.network.platformCall
import app.playlog.core.network.ensureSuccess
import app.playlog.platform.api.PlatformException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import java.time.ZoneId

/**
 * Nintendo Switch Parental Controls ("moon") API, read-only. It only sees consoles paired with the
 * Parental Controls app (restrictions may stay off) and keeps about 30 days of daily summaries.
 * Never call its POST endpoints: they change the console's restriction settings.
 */
internal class NintendoApi(
    private val client: HttpClient,
    private val auth: NintendoAuth,
    private val zone: () -> ZoneId,
    private val baseUrl: String = "https://app.lp1.znma.srv.nintendo.net/v2/actions",
    private val language: String = "en-US",
) {
    suspend fun ownedDevices(): List<OwnedDeviceDto> =
        get<OwnedDevicesResponse>("user/fetchOwnedDevices").ownedDevices

    suspend fun dailySummaries(deviceId: String): List<DailySummaryDto> =
        get<DailySummariesResponse>("playSummary/fetchDailySummaries") { parameter("deviceId", deviceId) }.dailySummaries

    private suspend inline fun <reified T> get(path: String, noinline configure: HttpRequestBuilder.() -> Unit = {}): T =
        platformCall(Platform.NINTENDO) { fetch(path, configure).body<T>() }

    /** Successful response for [path]; on 401/403 the id token is re-minted once. */
    private suspend fun fetch(path: String, configure: HttpRequestBuilder.() -> Unit): HttpResponse {
        suspend fun attempt(force: Boolean): HttpResponse {
            val token = auth.idToken(force)
            return platformCall(Platform.NINTENDO) {
                client.get("$baseUrl/$path") {
                    bearerAuth(token)
                    header(HttpHeaders.UserAgent, auth.userAgent)
                    header("X-Moon-App-Id", "com.nintendo.znma")
                    header("X-Moon-Os", "ANDROID")
                    header("X-Moon-Os-Version", "34")
                    header("X-Moon-Model", "Pixel 8")
                    header("X-Moon-App-Display-Version", "2.6.0")
                    header("X-Moon-App-Internal-Version", "700")
                    header("X-Moon-TimeZone", zone().id)
                    header("X-Moon-Os-Language", language)
                    header("X-Moon-App-Language", language)
                    configure()
                }.also { it.ensureSuccess(Platform.NINTENDO) }
            }
        }
        return try {
            attempt(false)
        } catch (e: PlatformException.AuthExpired) {
            attempt(true)
        }
    }
}
