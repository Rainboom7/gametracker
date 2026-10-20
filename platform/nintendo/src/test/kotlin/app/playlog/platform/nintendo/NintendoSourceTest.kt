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
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
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
                path.endsWith("/play_histories") -> respond(fixture("play_histories.json"), HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }
        NintendoSource.create(HttpClientFactory.create(engine), store, clock)
    }

    private fun redirectFor(flow: LinkFlow, state: String = flow.state.getValue("state")) =
        "npf5c38e31cd085304b://auth#session_state=xyz&session_token_code=STC&state=$state"

    private suspend fun link(): LinkFlow {
        val flow = source.linker.start()
        source.linker.complete(flow, mapOf(LinkFieldType.NINTENDO_REDIRECT_URL to redirectFor(flow)))
        return flow
    }

    @Test
    fun `login url uses PKCE S256 and captures the npf redirect`() = runTest {
        val flow = source.linker.start()
        val step = flow.steps.first { it.captureField == LinkFieldType.NINTENDO_REDIRECT_URL }
        assertEquals("npf5c38e31cd085304b://auth", step.captureRedirectPrefix)

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
        assertEquals("com.nintendo.znej/1.13.0 (Android/7.1.2)", sessionRequest.headers[HttpHeaders.UserAgent])
        assertTrue(flow.state.containsKey("verifier"))
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
    fun `play histories map to games with offsets parsed`() = runTest {
        link()
        val games = source.fetchLibrary()

        val totk = games.first { it.id == GameId(Platform.NINTENDO, "0100F2C0115B6000") }
        assertEquals(Duration.ofMinutes(12035), totk.playtime)
        assertEquals(Instant.parse("2025-10-18T13:40:00Z"), totk.lastPlayedAt)
        assertEquals(setOf("Switch"), totk.devices)
        assertEquals(2, games.size)
    }

    @Test
    fun `recent days become exact daily playtime`() = runTest {
        link()
        val daily = source.fetchDailyPlaytime(LocalDate.parse("2025-10-01"))
        assertEquals(
            listOf(DailyPlaytime(GameId(Platform.NINTENDO, "0100F2C0115B6000"), LocalDate.parse("2025-10-18"), Duration.ofMinutes(95))),
            daily,
        )
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
