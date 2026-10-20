package app.playlog.platform.steam

import app.playlog.core.model.GameId
import app.playlog.core.model.Platform
import app.playlog.core.network.HttpClientFactory
import app.playlog.platform.api.InMemoryCredentialStore
import app.playlog.platform.api.LinkFieldType
import app.playlog.platform.api.PlatformException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SteamSourceTest {
    private val key = "0123456789ABCDEF0123456789ABCDEF"
    private val steamId = "76561190000000000"
    private val requests = mutableListOf<HttpRequestData>()

    private fun fixture(name: String) = javaClass.getResource("/fixtures/steam/$name")!!.readText()

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun source(routes: Map<String, String>): Pair<SteamSource, InMemoryCredentialStore> {
        val engine = MockEngine { request ->
            requests += request
            val body = routes[request.url.encodedPath] ?: return@MockEngine json("{}", HttpStatusCode.NotFound)
            json(body)
        }
        val store = InMemoryCredentialStore()
        return SteamSource(HttpClientFactory.create(engine), store) to store
    }

    private val summary = """{"response":{"players":[{"steamid":"$steamId","personaname":"Gabe","avatarfull":"https://a/full.jpg","communityvisibilitystate":3}]}}"""

    private suspend fun SteamSource.link(profile: String = steamId) =
        linker.complete(linker.start(), mapOf(LinkFieldType.STEAM_API_KEY to key, LinkFieldType.STEAM_PROFILE to profile))

    @Test
    fun `library maps minutes, devices, artwork and last played`() = runTest {
        val (source, _) = source(
            mapOf(
                "/ISteamUser/GetPlayerSummaries/v2/" to summary,
                "/IPlayerService/GetOwnedGames/v1/" to fixture("get_owned_games.json"),
            ),
        )
        source.link()
        val games = source.fetchLibrary()

        assertEquals(3, games.size)
        val portal = games.first { it.id == GameId(Platform.STEAM, "620") }
        assertEquals("Portal 2", portal.title)
        assertEquals(Duration.ofMinutes(1834), portal.playtime)
        assertEquals(setOf("Windows", "Steam Deck"), portal.devices)
        assertEquals(Instant.ofEpochSecond(1727000000), portal.lastPlayedAt)
        assertTrue(portal.artwork.coverUrl!!.endsWith("/620/library_600x900_2x.jpg"))
        assertTrue(portal.artwork.bannerUrl!!.endsWith("/620/header.jpg"))
        assertEquals(Duration.ofMinutes(95), games.first { it.id.externalId == "1245620" }.recentPlaytime)

        val owned = requests.last { it.url.encodedPath.contains("GetOwnedGames") }.url.parameters
        assertEquals(key, owned["key"])
        assertEquals(steamId, owned["steamid"])
        assertEquals("1", owned["include_played_free_games"])
    }

    @Test
    fun `empty response from a private profile is reported as PrivateProfile`() = runTest {
        val privateSummary = summary.replace("\"communityvisibilitystate\":3", "\"communityvisibilitystate\":1")
        val (source, _) = source(
            mapOf(
                "/ISteamUser/GetPlayerSummaries/v2/" to privateSummary,
                "/IPlayerService/GetOwnedGames/v1/" to fixture("get_owned_games_private.json"),
            ),
        )
        source.link()
        try {
            source.fetchLibrary()
            fail("expected PrivateProfile")
        } catch (e: PlatformException.PrivateProfile) {
            assertEquals(Platform.STEAM, e.platform)
        }
    }

    @Test
    fun `vanity names and profile urls resolve to SteamID64`() = runTest {
        val (source, _) = source(
            mapOf(
                "/ISteamUser/ResolveVanityURL/v1/" to fixture("resolve_vanity.json"),
                "/ISteamUser/GetPlayerSummaries/v2/" to summary,
            ),
        )
        val account = source.link("https://steamcommunity.com/id/gabelogannewell/")
        assertEquals(steamId, account.userId)
        assertEquals("Gabe", account.displayName)
        assertEquals("gabelogannewell", requests.first().url.parameters["vanityurl"])
    }

    @Test
    fun `unknown vanity name is an input error on the profile field`() = runTest {
        val (source, store) = source(mapOf("/ISteamUser/ResolveVanityURL/v1/" to fixture("resolve_vanity_not_found.json")))
        try {
            source.link("nobody-here")
            fail("expected InvalidInput")
        } catch (e: PlatformException.InvalidInput) {
            assertEquals(LinkFieldType.STEAM_PROFILE, e.field)
        }
        assertNull(store.read(Platform.STEAM))
    }

    @Test
    fun `malformed api key is rejected before any request`() = runTest {
        val (source, _) = source(emptyMap())
        try {
            source.linker.complete(source.linker.start(), mapOf(LinkFieldType.STEAM_API_KEY to "nope", LinkFieldType.STEAM_PROFILE to steamId))
            fail("expected InvalidInput")
        } catch (e: PlatformException.InvalidInput) {
            assertEquals(LinkFieldType.STEAM_API_KEY, e.field)
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `screenshots request filetype 4 and keep only screenshot items`() = runTest {
        val (source, _) = source(
            mapOf(
                "/ISteamUser/GetPlayerSummaries/v2/" to summary,
                "/IPublishedFileService/GetUserFiles/v1/" to fixture("user_files_screenshots.json"),
            ),
        )
        source.link()
        val shots = source.fetchScreenshots()

        assertTrue(shots.isNotEmpty())
        assertTrue(shots.all { it.platform == Platform.STEAM && it.fullUrl.startsWith("https://") })
        assertEquals(GameId(Platform.STEAM, "1245620"), shots.first().gameId)
        assertEquals("4", requests.last().url.parameters["filetype"])
    }

    @Test
    fun `fetching without a linked account throws NotLinked`() = runTest {
        val (source, _) = source(emptyMap())
        try {
            source.fetchLibrary()
            fail("expected NotLinked")
        } catch (e: PlatformException.NotLinked) {
            assertEquals(Platform.STEAM, e.platform)
        }
    }

    @Test
    fun `rejected key maps to AuthExpired during sync`() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("GetPlayerSummaries")) {
                respond(summary, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond("<html>Forbidden</html>", HttpStatusCode.Forbidden)
            }
        }
        val source = SteamSource(HttpClientFactory.create(engine), InMemoryCredentialStore())
        source.link()
        try {
            source.fetchLibrary()
            fail("expected AuthExpired")
        } catch (e: PlatformException.AuthExpired) {
            assertEquals(Platform.STEAM, e.platform)
        }
    }
}
