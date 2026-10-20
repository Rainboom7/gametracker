package app.playlog.platform.nintendo

import app.playlog.core.model.Platform
import app.playlog.core.network.platformCall
import app.playlog.core.network.successBody
import app.playlog.platform.api.PlatformException
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

internal class NintendoApi(
    private val client: HttpClient,
    private val auth: NintendoAuth,
    private val baseUrl: String = "https://mypage-api.entry.nintendo.co.jp/api/v1",
) {
    /** Lifetime totals per title plus a per-day breakdown of the last week or so. */
    suspend fun playHistories(): PlayHistoriesResponse {
        suspend fun attempt(force: Boolean): PlayHistoriesResponse {
            val token = auth.accessToken(force)
            return platformCall(Platform.NINTENDO) {
                client.get("$baseUrl/users/me/play_histories") {
                    bearerAuth(token)
                    header(HttpHeaders.Accept, "application/json")
                    header(HttpHeaders.UserAgent, auth.userAgent)
                }.successBody(Platform.NINTENDO)
            }
        }
        return try {
            attempt(false)
        } catch (e: PlatformException.AuthExpired) {
            attempt(true)
        }
    }
}
