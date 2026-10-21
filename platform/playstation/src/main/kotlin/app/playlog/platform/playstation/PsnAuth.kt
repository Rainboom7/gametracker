package app.playlog.platform.playstation

import app.playlog.core.common.AppClock
import app.playlog.core.model.Platform
import app.playlog.core.network.bodySnippet
import app.playlog.core.network.TrackerJson
import app.playlog.core.network.platformCall
import app.playlog.core.network.successBody
import app.playlog.platform.api.PlatformException
import app.playlog.platform.api.TypedCredentials
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

@Serializable
internal data class PsnCredentials(
    /**
     * Long-lived (≈ 2 months) SSO cookie, only when the user pasted it; used to mint new tokens
     * when the refresh token dies. WebView sign-in leaves it null and relies on the refresh token.
     */
    val npsso: String? = null,
    val accessToken: String? = null,
    val accessTokenExpiresAt: Long = 0,
    val refreshToken: String? = null,
    val refreshTokenExpiresAt: Long = 0,
    val onlineId: String? = null,
    val avatarUrl: String? = null,
)

/**
 * OAuth flow of the official PlayStation App: `npsso` cookie → authorization code → JWT access /
 * refresh tokens. Tokens are cached in the credential store and refreshed on demand.
 */
internal class PsnAuth(
    private val client: HttpClient,
    private val credentials: TypedCredentials<PsnCredentials>,
    private val clock: AppClock,
    private val authBaseUrl: String = "https://ca.account.sony.com/api/authz/v3/oauth",
) {
    private val mutex = Mutex()

    /** Sign-in page for the WebView flow; it ends with a redirect to [REDIRECT_URI] carrying `code`. */
    fun authorizeUrl(): String = URLBuilder("$authBaseUrl/authorize").apply {
        authorizeParameters().forEach { (k, v) -> parameters.append(k, v) }
    }.buildString()

    /** Exchanges an `npsso` for fresh tokens without saving anything. */
    suspend fun exchangeNpsso(npsso: String): PsnCredentials =
        exchangeCode(authorizationCode(npsso)).copy(npsso = npsso)

    /** Exchanges the `code` from the sign-in redirect for tokens without saving anything. */
    suspend fun exchangeCode(code: String): PsnCredentials {
        val tokens = requestTokens(
            "code" to code,
            "redirect_uri" to REDIRECT_URI,
            "grant_type" to "authorization_code",
            "token_format" to "jwt",
        )
        return PsnCredentials().withTokens(tokens)
    }

    /** A valid access token, refreshing or re-minting it when needed. */
    suspend fun accessToken(forceRefresh: Boolean = false): String = mutex.withLock {
        val current = credentials.require()
        val now = clock.now().epochSecond
        if (!forceRefresh && current.accessToken != null && current.accessTokenExpiresAt - EXPIRY_MARGIN_S > now) {
            return current.accessToken
        }
        val updated = refresh(current, now) ?: run {
            val npsso = current.npsso
                ?: throw PlatformException.AuthExpired(Platform.PLAYSTATION, "Refresh token expired, sign in again")
            try {
                exchangeNpsso(npsso).copy(onlineId = current.onlineId, avatarUrl = current.avatarUrl)
            } catch (e: PlatformException.InvalidInput) {
                throw PlatformException.AuthExpired(Platform.PLAYSTATION, "npsso expired, sign in again", e)
            }
        }
        credentials.write(updated)
        updated.accessToken!!
    }

    private suspend fun refresh(current: PsnCredentials, now: Long): PsnCredentials? {
        val refreshToken = current.refreshToken ?: return null
        if (current.refreshTokenExpiresAt - EXPIRY_MARGIN_S <= now) return null
        return try {
            current.withTokens(
                requestTokens(
                    "refresh_token" to refreshToken,
                    "grant_type" to "refresh_token",
                    "scope" to SCOPE,
                    "token_format" to "jwt",
                ),
            )
        } catch (e: PlatformException.AuthExpired) {
            null
        } catch (e: PlatformException.Protocol) {
            null
        }
    }

    private suspend fun authorizationCode(npsso: String): String = platformCall(Platform.PLAYSTATION) {
        val response = client.get("$authBaseUrl/authorize") {
            authorizeParameters().forEach { (k, v) -> parameter(k, v) }
            header(HttpHeaders.Cookie, "npsso=$npsso")
        }
        val location = response.headers[HttpHeaders.Location]
            ?: throw PlatformException.Protocol(Platform.PLAYSTATION, "No redirect from authorize (HTTP ${response.status.value}): ${response.bodySnippet()}")
        Url(location).parameters["code"]
            ?: throw PlatformException.InvalidInput(Platform.PLAYSTATION, null, "npsso rejected: ${Url(location).parameters["error"] ?: location}")
    }

    private suspend fun requestTokens(vararg form: Pair<String, String>): PsnTokenResponse = platformCall(Platform.PLAYSTATION) {
        client.submitForm(
            url = "$authBaseUrl/token",
            formParameters = parameters { form.forEach { (k, v) -> append(k, v) } },
        ) {
            header(HttpHeaders.Authorization, "Basic $BASIC_AUTH")
        }.successBody<PsnTokenResponse>(Platform.PLAYSTATION)
    }

    private fun authorizeParameters() = listOf(
        "access_type" to "offline",
        "client_id" to CLIENT_ID,
        "redirect_uri" to REDIRECT_URI,
        "response_type" to "code",
        "scope" to SCOPE,
    )

    private fun PsnCredentials.withTokens(tokens: PsnTokenResponse): PsnCredentials {
        val now = clock.now().epochSecond
        return copy(
            accessToken = tokens.accessToken,
            accessTokenExpiresAt = now + tokens.expiresIn,
            refreshToken = tokens.refreshToken ?: refreshToken,
            refreshTokenExpiresAt = tokens.refreshTokenExpiresIn?.let { now + it } ?: refreshTokenExpiresAt,
        )
    }

    companion object {
        private val ACCOUNT_ID = Regex("^\\d{6,25}$")

        /** PSN account id from a JWT access token: `sub`, or `account_id` on some token versions. */
        fun accountIdFromJwt(token: String): String? {
            val payload = token.split('.').getOrNull(1) ?: return null
            val claims = runCatching {
                TrackerJson.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload.trimEnd('=')))).jsonObject
            }.getOrNull() ?: return null
            return listOf("sub", "account_id", "accountId")
                .firstNotNullOfOrNull { key -> claims[key]?.jsonPrimitive?.contentOrNull?.takeIf(ACCOUNT_ID::matches) }
        }

        /** Extracts `code` from a captured `com.scee.psxandroid.scecompcall://redirect/?code=…` URL. */
        fun codeFromRedirect(url: String): String? =
            url.trim().takeIf { it.startsWith(REDIRECT_SCHEME) }?.let { runCatching { Url(it).parameters["code"] }.getOrNull() }

        const val REDIRECT_SCHEME = "com.scee.psxandroid.scecompcall://"

        // Public client of the PlayStation App for Android (widely documented, e.g. psn-api).
        const val CLIENT_ID = "09515159-7237-4370-9b40-3806e67c0891"
        const val REDIRECT_URI = "com.scee.psxandroid.scecompcall://redirect"
        const val SCOPE = "psn:mobile.v2.core psn:clientapp"
        private const val BASIC_AUTH = "MDk1MTUxNTktNzIzNy00MzcwLTliNDAtMzgwNmU2N2MwODkxOnVjUGprYTV0bnRCMktxc1A="
        private const val EXPIRY_MARGIN_S = 60L
    }
}
