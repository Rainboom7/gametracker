package app.playlog.platform.nintendo

import app.playlog.core.common.AppClock
import app.playlog.core.model.Platform
import app.playlog.core.network.platformCall
import app.playlog.core.network.successBody
import app.playlog.platform.api.PlatformException
import app.playlog.platform.api.TypedCredentials
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.http.parseQueryString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Serializable
internal data class NintendoCredentials(
    /** Long-lived (≈ 2 years) Nintendo Account session token. */
    val sessionToken: String,
    val accessToken: String? = null,
    val accessTokenExpiresAt: Long = 0,
    val userId: String? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null,
)

/**
 * Nintendo Account "session token" login used by Nintendo's own mobile apps: PKCE authorize URL →
 * user copies the `npf<client>://auth#session_token_code=…` link → session token → access token.
 */
internal class NintendoAuth(
    private val client: HttpClient,
    private val credentials: TypedCredentials<NintendoCredentials>,
    private val clock: AppClock,
    private val config: NintendoClientConfig = NintendoClientConfig.PLAY_HISTORY,
    private val accountsBaseUrl: String = "https://accounts.nintendo.com/connect/1.0.0",
    private val usersBaseUrl: String = "https://api.accounts.nintendo.com/2.0.0",
    private val random: SecureRandom = SecureRandom(),
) {
    private val mutex = Mutex()

    val redirectUri: String get() = config.redirectUri
    val userAgent: String get() = config.userAgent

    data class LoginRequest(val url: String, val state: String, val verifier: String)

    fun createLoginRequest(): LoginRequest {
        val state = randomToken(36)
        val verifier = randomToken(32)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val url = URLBuilder("$accountsBaseUrl/authorize").apply {
            parameters.append("state", state)
            parameters.append("redirect_uri", config.redirectUri)
            parameters.append("client_id", config.clientId)
            parameters.append("scope", config.scope)
            parameters.append("response_type", "session_token_code")
            parameters.append("session_token_code_challenge", challenge)
            parameters.append("session_token_code_challenge_method", "S256")
            parameters.append("theme", "login_form")
        }.buildString()
        return LoginRequest(url, state, verifier)
    }

    /** Pulls `session_token_code` out of the pasted redirect link, checking `state`. */
    fun parseRedirect(link: String, expectedState: String): String? {
        val trimmed = link.trim()
        if (!trimmed.startsWith(config.redirectUri)) return null
        val fragment = trimmed.substringAfter('#', missingDelimiterValue = trimmed.substringAfter('?', ""))
        val params = parseQueryString(fragment)
        if (params["state"] != null && params["state"] != expectedState) return null
        return params["session_token_code"]
    }

    suspend fun sessionToken(sessionTokenCode: String, verifier: String): String = platformCall(Platform.NINTENDO) {
        client.submitForm(
            url = "$accountsBaseUrl/api/session_token",
            formParameters = parameters {
                append("client_id", config.clientId)
                append("session_token_code", sessionTokenCode)
                append("session_token_code_verifier", verifier)
            },
        ) {
            header(HttpHeaders.UserAgent, config.userAgent)
        }.successBody<SessionTokenResponse>(Platform.NINTENDO).sessionToken
    }

    suspend fun mintAccessToken(sessionToken: String): AccessTokenResponse = platformCall(Platform.NINTENDO) {
        client.post("$accountsBaseUrl/api/token") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.UserAgent, config.userAgent)
            setBody(AccessTokenRequest(config.clientId, sessionToken))
        }.successBody(Platform.NINTENDO)
    }

    suspend fun me(accessToken: String): NintendoUserDto = platformCall(Platform.NINTENDO) {
        client.get("$usersBaseUrl/users/me") {
            bearerAuth(accessToken)
            header(HttpHeaders.UserAgent, config.userAgent)
        }.successBody(Platform.NINTENDO)
    }

    suspend fun accessToken(forceRefresh: Boolean = false): String = mutex.withLock {
        val current = credentials.require()
        val now = clock.now().epochSecond
        if (!forceRefresh && current.accessToken != null && current.accessTokenExpiresAt - 60 > now) {
            return current.accessToken
        }
        val minted = try {
            mintAccessToken(current.sessionToken)
        } catch (e: PlatformException.Protocol) {
            // 400 invalid_grant: session token revoked (password change, manual sign-out).
            throw PlatformException.AuthExpired(Platform.NINTENDO, "Session token rejected", e)
        }
        credentials.write(current.copy(accessToken = minted.accessToken, accessTokenExpiresAt = now + minted.expiresIn))
        minted.accessToken
    }

    private fun randomToken(bytes: Int): String = ByteArray(bytes).also(random::nextBytes).let(::base64Url)

    private fun base64Url(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/** Which Nintendo app we sign in as (data-sources/SPEC.md §4). Each client needs its own session token. */
internal data class NintendoClientConfig(val clientId: String, val scope: String, val userAgent: String) {
    val redirectUri get() = "npf$clientId://auth"

    companion object {
        /** Nintendo Store app: can read `play_histories` (lifetime totals + recent days). */
        val PLAY_HISTORY = NintendoClientConfig(
            clientId = "5c38e31cd085304b",
            scope = "openid user user.mii user.email user.links[].id",
            userAgent = "com.nintendo.znej/1.13.0 (Android/7.1.2)",
        )
    }
}
