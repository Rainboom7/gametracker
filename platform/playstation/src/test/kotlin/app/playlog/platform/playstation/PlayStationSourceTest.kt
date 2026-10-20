package app.playlog.platform.playstation

import app.playlog.core.common.AppClock
import app.playlog.core.model.Platform
import app.playlog.core.network.HttpClientFactory
import app.playlog.platform.api.InMemoryCredentialStore
import app.playlog.platform.api.LinkFieldType
import app.playlog.platform.api.PlatformException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class PlayStationSourceTest {
    private val npsso = "a".repeat(64)
    private val requests = mutableListOf<HttpRequestData>()
    private var now = Instant.parse("2025-10-20T12:00:00Z")
    private val clock = object : AppClock {
        override fun now() = now
        override fun zone(): ZoneId = ZoneOffset.UTC
    }

    /** Overrides per path; anything else falls back to the happy-path fixtures. */
    private val overrides = mutableMapOf<String, Pair<HttpStatusCode, String>>()

    private fun fixture(name: String) = javaClass.getResource("/fixtures/psn/$name")!!.readText()

    private val store = InMemoryCredentialStore()
    private val source by lazy {
        val engine = MockEngine { request ->
            requests += request
            val path = request.url.encodedPath
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            overrides[path]?.let { (status, body) -> return@MockEngine respond(body, status, json) }
            when {
                path.endsWith("/oauth/authorize") ->
                    if (request.headers[HttpHeaders.Cookie] == "npsso=$npsso") {
                        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "com.scee.psxandroid.scecompcall://redirect/?code=v3.CODE&cid=x"))
                    } else {
                        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "com.scee.psxandroid.scecompcall://redirect/?error=login_required"))
                    }
                path.endsWith("/oauth/token") -> respond(fixture("token.json"), HttpStatusCode.OK, json)
                path.endsWith("/users/me/profiles") ->
                    respond("""{"onlineId":"kratos","avatars":[{"size":"s","url":"https://a/s.png"},{"size":"xl","url":"https://a/xl.png"}]}""", HttpStatusCode.OK, json)
                path.endsWith("/gamelist/v2/users/me/titles") ->
                    respond(fixture(if (request.url.parameters["offset"] == "0") "played_titles_page1.json" else "played_titles_page2.json"), HttpStatusCode.OK, json)
                path.endsWith("/trophyTitles") ->
                    respond("""{"trophyTitles":[{"trophyTitleName":"Marvel's Spider-Man 2™","progress":87}]}""", HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }
        PlayStationSource.create(HttpClientFactory.create(engine), store, clock)
    }

    private suspend fun linkWithNpsso() =
        source.linker.complete(source.linker.start(), mapOf(LinkFieldType.PSN_NPSSO to npsso))

    @Test
    fun `npsso link exchanges code for tokens and stores profile`() = runTest {
        val account = linkWithNpsso()
        assertEquals("kratos", account.displayName)
        assertEquals("https://a/xl.png", account.avatarUrl)

        val tokenRequest = requests.first { it.url.encodedPath.endsWith("/token") }
        val form = (tokenRequest.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        assertTrue(form.contains("code=v3.CODE"))
        assertTrue(form.contains("grant_type=authorization_code"))
        assertTrue(tokenRequest.headers[HttpHeaders.Authorization]!!.startsWith("Basic "))
        assertNotNull(store.read(Platform.PLAYSTATION))
    }

    @Test
    fun `npsso pasted as ssocookie json is accepted`() {
        assertEquals(npsso, PlayStationSource.parseNpsso("""{"npsso":"$npsso"}"""))
        assertNull(PlayStationSource.parseNpsso("short"))
    }

    @Test
    fun `rejected npsso is an input error`() = runTest {
        try {
            source.linker.complete(source.linker.start(), mapOf(LinkFieldType.PSN_NPSSO to "b".repeat(64)))
            fail("expected InvalidInput")
        } catch (e: PlatformException.InvalidInput) {
            assertEquals(LinkFieldType.PSN_NPSSO, e.field)
        }
        assertNull(store.read(Platform.PLAYSTATION))
    }

    @Test
    fun `webview redirect link is exchanged without npsso`() = runTest {
        val flow = source.linker.start()
        val capture = flow.steps.first { it.captureField == LinkFieldType.PSN_REDIRECT_URL }
        assertTrue(capture.url!!.contains("client_id=${PsnAuth.CLIENT_ID}"))

        val account = source.linker.complete(flow, mapOf(LinkFieldType.PSN_REDIRECT_URL to "com.scee.psxandroid.scecompcall://redirect/?code=v3.WEB&cid=1"))
        assertEquals("kratos", account.userId)
        assertTrue(requests.none { it.url.encodedPath.endsWith("/authorize") })
    }

    @Test
    fun `library follows pagination, parses durations and merges PS4 and PS5 by concept`() = runTest {
        linkWithNpsso()
        val games = source.fetchLibrary()

        val offsets = requests.filter { it.url.encodedPath.endsWith("/titles") }.map { it.url.parameters["offset"] }
        assertEquals(listOf("0", "2"), offsets)

        val spiderMan = games.first { it.title == "Marvel's Spider-Man 2" }
        assertEquals("concept:10002456", spiderMan.id.externalId)
        assertTrue(spiderMan.playtime >= Duration.ofHours(243).plusMinutes(18).plusSeconds(48))
        assertEquals("https://image.api.playstation.com/vulcan/ap/rnd/202306/1219/portrait.jpg", spiderMan.artwork.coverUrl)
        assertEquals(Instant.parse("2023-10-20T08:00:00Z"), spiderMan.firstPlayedAt)
        assertEquals(87, spiderMan.achievementProgress)
        assertTrue("PS5" in spiderMan.devices)

        val astro = games.first { it.title == "ASTRO BOT" }
        assertEquals(Duration.ofHours(14).plusMinutes(5), astro.playtime)
        assertEquals("https://image.api.playstation.com/vulcan/ap/rnd/202406/astro/cover.jpg", astro.artwork.coverUrl)
    }

    @Test
    fun `HTTP 200 with a privacy error becomes PrivateProfile`() = runTest {
        linkWithNpsso()
        overrides["/api/gamelist/v2/users/me/titles"] = HttpStatusCode.OK to fixture("error_200_privacy.json")
        try {
            source.fetchLibrary()
            fail("expected PrivateProfile")
        } catch (e: PlatformException.PrivateProfile) {
            assertEquals(Platform.PLAYSTATION, e.platform)
        }
    }

    @Test
    fun `expired access token is refreshed with the refresh token`() = runTest {
        linkWithNpsso()
        now = now.plusSeconds(3600 * 2)
        requests.clear()

        source.fetchLibrary()

        val refresh = requests.first { it.url.encodedPath.endsWith("/token") }
        val form = (refresh.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        assertTrue(form.contains("grant_type=refresh_token"))
        assertTrue(requests.none { it.url.encodedPath.endsWith("/authorize") })
    }

    @Test
    fun `dead refresh token without npsso asks the user to sign in again`() = runTest {
        source.linker.complete(source.linker.start(), mapOf(LinkFieldType.PSN_REDIRECT_URL to "com.scee.psxandroid.scecompcall://redirect/?code=v3.WEB"))
        now = now.plusSeconds(3600 * 2)
        overrides["/api/authz/v3/oauth/token"] = HttpStatusCode.BadRequest to fixture("token_invalid_grant.json")
        try {
            source.fetchLibrary()
            fail("expected AuthExpired")
        } catch (e: PlatformException.AuthExpired) {
            assertEquals(Platform.PLAYSTATION, e.platform)
        }
    }
}
