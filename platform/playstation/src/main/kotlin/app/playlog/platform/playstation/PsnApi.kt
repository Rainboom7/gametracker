package app.playlog.platform.playstation

import app.playlog.core.model.Platform
import app.playlog.core.network.platformCall
import app.playlog.core.network.successBody
import app.playlog.core.network.TrackerJson
import app.playlog.platform.api.PlatformException
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders

/** Endpoints used by the PlayStation App (m.np.playstation.com). */
internal class PsnApi(
    private val client: HttpClient,
    private val auth: PsnAuth,
    private val baseUrl: String = "https://m.np.playstation.com/api",
    private val language: String = "en-US",
    private val diagnostics: (String) -> Unit = {},
    private val legacyProfileUrl: String = "https://us-prof.np.community.playstation.net/userProfile/v1/users/me/profile2",
) {
    /**
     * The signed-in user's profile, or null when PSN will not tell us. `internal/users/{id}/profiles`
     * rejects `me` (400 "path accountId"), so it is called with the account id from the JWT; the
     * legacy profile2 endpoint, which does accept `me`, is the fallback.
     */
    suspend fun profile(accessToken: String): PsnProfileDto? {
        PsnAuth.accountIdFromJwt(accessToken)?.let { accountId ->
            try {
                return platformCall(Platform.PLAYSTATION) {
                    client.get("$baseUrl/userProfile/v1/internal/users/$accountId/profiles") {
                        bearerAuth(accessToken)
                        header(HttpHeaders.AcceptLanguage, language)
                    }.psnBody<PsnProfileDto>()
                }
            } catch (_: PlatformException) {
                // fall through to the legacy endpoint
            }
        }
        return try {
            platformCall(Platform.PLAYSTATION) {
                client.get(legacyProfileUrl) {
                    bearerAuth(accessToken)
                    parameter("fields", "onlineId,accountId,avatarUrls")
                }.psnBody<PsnLegacyProfileResponse>().profile.toProfile()
            }
        } catch (_: PlatformException) {
            null
        }
    }

    suspend fun playedTitles(offset: Int, limit: Int): PsnTitlesPage = authorized {
        client.get("$baseUrl/gamelist/v2/users/me/titles") {
            it(this)
            parameter("categories", "ps4_game,ps5_native_game,pspc_game")
            parameter("limit", limit)
            parameter("offset", offset)
        }.psnBody()
    }

    suspend fun trophyTitles(offset: Int, limit: Int): PsnTrophyTitlesPage = authorized {
        client.get("$baseUrl/trophy/v1/users/me/trophyTitles") {
            it(this)
            parameter("limit", limit)
            parameter("offset", offset)
        }.psnBody()
    }

    /** PS5 captures auto-uploaded to the cloud media gallery (kept by Sony for ~14 days). */
    suspend fun mediaGallery(cursor: String?, limit: Int): PsnGalleryResult = authorized {
        val response = client.get("$baseUrl/gameMediaService/v2/c2s/category/cloudMediaGallery/ugcType/all") {
            it(this)
            parameter("includeTokenizedUrls", true)
            parameter("limit", limit)
            cursor?.let { c -> parameter("cursorMark", c) }
        }
        val json = response.psnBody<JsonObject>()
        val setCookies = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        diagnostics(galleryShape(json, setCookies))
        PsnGalleryResult(
            page = TrackerJson.decodeFromJsonElement(json),
            signing = CloudFrontSigning.from(setCookies, json),
        )
    }

    /** Structure only (key and cookie names, URL query parameter names): values hold signed tokens. */
    private fun galleryShape(json: JsonObject, setCookies: List<String>): String {
        val first = (json["ugcDocument"] as? JsonArray)?.firstOrNull() as? JsonObject
        val urlParams = first?.values?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { it.startsWith("http") }
            ?.map { url -> url.substringAfter('?', "").split('&').map { p -> p.substringBefore('=') }.filter(String::isNotEmpty) }
        return "PSN gallery: keys=${json.keys}, item keys=${first?.keys}, set-cookie=${setCookies.map { it.substringBefore('=') }}, " +
            "url params=$urlParams"
    }

    /** PSN sometimes answers HTTP 200 with `{"error":{"code":…,"message":…}}`. */
    private suspend inline fun <reified T> HttpResponse.psnBody(): T {
        val obj = successBody<JsonObject>(Platform.PLAYSTATION)
        val error = obj["error"]?.jsonObject ?: return TrackerJson.decodeFromJsonElement(obj)
        val code = error["code"]?.jsonPrimitive?.intOrNull
        val message = error["message"]?.jsonPrimitive?.content ?: "unknown"
        throw when {
            code in PRIVACY_ERROR_CODES || message.contains("privacy", ignoreCase = true) ->
                PlatformException.PrivateProfile(Platform.PLAYSTATION)
            code in AUTH_ERROR_CODES || message.contains("token", ignoreCase = true) ->
                PlatformException.AuthExpired(Platform.PLAYSTATION, "PSN error $code: $message")
            else -> PlatformException.Protocol(Platform.PLAYSTATION, "PSN error $code: $message")
        }
    }

    /** Runs [request] with a bearer token; on 401/403 refreshes once and retries. */
    private suspend fun <T> authorized(request: suspend (configure: HttpRequestBuilder.() -> Unit) -> T): T {
        suspend fun attempt(force: Boolean): T {
            val token = auth.accessToken(forceRefresh = force)
            return platformCall(Platform.PLAYSTATION) {
                request {
                    bearerAuth(token)
                    header(HttpHeaders.AcceptLanguage, language)
                }
            }
        }
        return try {
            attempt(force = false)
        } catch (e: PlatformException.AuthExpired) {
            attempt(force = true)
        }
    }

    private companion object {
        // Codes seen in community clients; the message check above catches the rest.
        val PRIVACY_ERROR_CODES = setOf(2240526, 2241025)
        val AUTH_ERROR_CODES = setOf(2241037, 2240525)
    }
}
