package app.playlog.platform.nintendo

import app.playlog.core.common.AppClock
import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.GameId
import app.playlog.core.model.Platform
import app.playlog.core.network.HttpClientFactory
import app.playlog.platform.api.InMemoryCredentialStore
import app.playlog.platform.api.LinkFieldType
import app.playlog.platform.api.LinkFlow
import app.playlog.platform.api.PlatformException
import app.playlog.platform.api.TypedCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64

class NintendoSourceTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val overrides = mutableMapOf<String, Pair<HttpStatusCode, String>>()
    private var now = Instant.parse("2025-10-20T12:00:00Z")
    private val clock = object : AppClock {
        override fun now() = now
        override fun zone(): ZoneId = ZoneOffset.UTC
    }
    private val store = InMemoryCredentialStore()
    private var daily: String? = null

    private fun fixture(name: String) = javaClass.getResource("/fixtures/nintendo/$name")!!.readText()

    private val source by lazy {
        val engine = MockEngine { request ->
            requests += request
            val path = request.url.encodedPath
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            overrides[path]?.let { (status, body) -> return@MockEngine respond(body, status, json) }
            when {
                path.endsWith("/api/session_token") -> respond(fixture("session_token.json"), HttpStatusCode.OK, json)
                path.endsWith("/api/token") -> respond(fixture("token.json"), HttpStatusCode.OK, json)
                path.endsWith("/users/me") -> respond("""{"id":"abc123","nickname":"Link","mii":{"imageOrigin":"https://mii/1.png"}}""", HttpStatusCode.OK, json)
                path.endsWith("/fetchOwnedDevices") -> respond(fixture("parental_owned_devices.json"), HttpStatusCode.OK, json)
                path.endsWith("/fetchDailySummaries") -> respond(daily ?: fixture("parental_daily_summaries.json"), HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }
        NintendoSource.create(HttpClientFactory.create(engine), store, clock)
    }

    private fun redirectFor(flow: LinkFlow, state: String = flow.state.getValue("state")) =
        "npf54789befb391a838://auth#session_state=xyz&session_token_code=STC&state=$state"

    private suspend fun link(): LinkFlow {
        val flow = source.linker.start()
        source.linker.complete(flow, mapOf(LinkFieldType.NINTENDO_REDIRECT_URL to redirectFor(flow)))
        return flow
    }

    @Test
    fun `login url uses PKCE S256 and captures the npf redirect`() = runTest {
        val flow = source.linker.start()
        val step = flow.steps.first { it.captureField == LinkFieldType.NINTENDO_REDIRECT_URL }
        assertEquals("npf54789befb391a838://auth", step.captureRedirectPrefix)
        assertTrue(Url(step.url!!).parameters["scope"]!!.contains("moonDailySummary"))

        val params = Url(step.url!!).parameters
        val expected = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(flow.state.getValue("verifier").toByteArray()))
        assertEquals(expected, params["session_token_code_challenge"])
        assertEquals("S256", params["session_token_code_challenge_method"])
        assertEquals(flow.state["state"], params["state"])
        assertEquals("session_token_code", params["response_type"])
    }

    @Test
    fun `link exchanges the code and stores the account`() = runTest {
        val flow = link()
        val account = source.linker.current()!!
        assertEquals("Link", account.displayName)
        assertEquals("abc123", account.userId)

        val sessionRequest = requests.first { it.url.encodedPath.endsWith("/session_token") }
        assertEquals("moon_ANDROID/2.6.0 (com.nintendo.znma; build:700; ANDROID 34)", sessionRequest.headers[HttpHeaders.UserAgent])
        assertTrue(flow.state.containsKey("verifier"))

        // Regression: grant_type has to be in the JSON, or Nintendo answers 400 "request doesn't match schema".
        val tokenRequest = requests.first { it.url.encodedPath.endsWith("/api/token") }
        val body = (tokenRequest.body as TextContent).text
        assertTrue(body, body.contains("\"grant_type\":\"urn:ietf:params:oauth:grant-type:jwt-bearer-session-token\""))
        assertTrue(body, body.contains("\"session_token\""))
    }

    @Test
    fun `redirect with a foreign state is refused`() = runTest {
        val flow = source.linker.start()
        try {
            source.linker.complete(flow, mapOf(LinkFieldType.NINTENDO_REDIRECT_URL to redirectFor(flow, state = "evil")))
            fail("expected InvalidInput")
        } catch (e: PlatformException.InvalidInput) {
            assertEquals(LinkFieldType.NINTENDO_REDIRECT_URL, e.field)
        }
    }

    @Test
    fun `parental daily summaries become games and exact daily playtime`() = runTest {
        link()
        val games = source.fetchLibrary()

        val game = games.single()
        assertEquals(GameId(Platform.NINTENDO, "0100152000022000"), game.id)
        assertEquals("five", game.title)
        assertEquals(Duration.ofMinutes(15), game.playtime)
        assertEquals(Instant.parse("2025-12-08T00:00:00Z"), game.lastPlayedAt)
        assertEquals(setOf("Switch"), game.devices)
        assertEquals("https://atum-img-lp1.cdn.nintendo.net/i/c/fd75c85d0465407cba8249e93c21e831_1024", game.artwork.coverUrl)

        assertEquals(
            listOf(DailyPlaytime(game.id, LocalDate.parse("2025-12-08"), Duration.ofMinutes(15))),
            source.fetchDailyPlaytime(LocalDate.parse("2025-12-01")),
        )
        val request = requests.first { it.url.encodedPath.endsWith("/fetchDailySummaries") }
        assertEquals("DEVICE_ID_1", request.url.parameters["deviceId"])
        assertEquals("com.nintendo.znma", request.headers["X-Moon-App-Id"])
        assertEquals("Bearer <redacted:id_token>", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `history survives days leaving the 30-day window and today's total is replaced`() = runTest {
        link()
        source.fetchLibrary() // 2025-12-08: 15 min (still calculating)
        daily = summaries("2025-12-09" to 40L, "2025-12-08" to 30L)
        source.fetchLibrary()
        daily = summaries("2025-12-10" to 5L) // the 8th and 9th have left the window
        val game = source.fetchLibrary().single()

        assertEquals(Duration.ofMinutes(30 + 40 + 5), game.playtime)
        assertEquals(Instant.parse("2025-12-08T00:00:00Z"), game.firstPlayedAt)
        assertEquals(3, source.fetchDailyPlaytime(LocalDate.parse("2025-12-01")).size)
    }

    @Test
    fun `relinking the same account keeps the history`() = runTest {
        link()
        source.fetchLibrary()
        link()
        assertEquals(Duration.ofMinutes(15), source.fetchDailyPlaytime(LocalDate.parse("2025-12-01")).single().playtime)
    }

    @Test
    fun `no paired console is a clear error`() = runTest {
        link()
        overrides["/v2/actions/user/fetchOwnedDevices"] = HttpStatusCode.OK to """{"ownedDevices":[]}"""
        try {
            source.fetchLibrary()
            fail("expected Protocol")
        } catch (e: PlatformException.Protocol) {
            assertTrue(e.message!!.contains("Parental Controls"))
        }
    }

    @Test
    fun `account linked with the old Nintendo Store client must sign in again`() = runTest {
        TypedCredentials(store, Platform.NINTENDO, NintendoCredentials.serializer())
            .write(NintendoCredentials(sessionToken = "old", userId = "abc123", nickname = "Link"))
        try {
            source.fetchLibrary()
            fail("expected AuthExpired")
        } catch (e: PlatformException.AuthExpired) {
            assertEquals(Platform.NINTENDO, e.platform)
        }
    }

    private fun summaries(vararg days: Pair<String, Long>) = days.joinToString(",", """{"dailySummaries":[""", "]}") { (date, minutes) ->
        """{"date":"$date","result":"ACHIEVED","players":[{"playingTime":$minutes,"playedGames":[""" +
            """{"meta":{"applicationId":"0100152000022000","title":"five"},"playingTime":$minutes}]}]}"""
    }

    @Test
    fun `revoked session token maps to AuthExpired`() = runTest {
        link()
        now = now.plusSeconds(3600)
        overrides["/connect/1.0.0/api/token"] = HttpStatusCode.BadRequest to fixture("token_invalid_grant.json")
        try {
            source.fetchLibrary()
            fail("expected AuthExpired")
        } catch (e: PlatformException.AuthExpired) {
            assertEquals(Platform.NINTENDO, e.platform)
        }
    }
}
