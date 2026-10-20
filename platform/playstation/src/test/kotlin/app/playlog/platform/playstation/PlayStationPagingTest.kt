package app.playlog.platform.playstation

import app.playlog.core.common.AppClock
import app.playlog.core.model.Platform
import app.playlog.core.network.HttpClientFactory
import app.playlog.platform.api.InMemoryCredentialStore
import app.playlog.platform.api.PlatformException
import app.playlog.platform.api.TypedCredentials
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
import java.time.ZoneId
import java.time.ZoneOffset

/** Edge cases of PSN paging and title parsing that the happy-path fixtures don't reach. */
class PlayStationPagingTest {
    private val now = Instant.parse("2025-10-20T12:00:00Z")
    private val clock = object : AppClock {
        override fun now() = now
        override fun zone(): ZoneId = ZoneOffset.UTC
    }
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()

    private fun MockRequestHandleScope.ok(body: String) = respond(body, HttpStatusCode.OK, json)

    private suspend fun source(
        titles: MockRequestHandleScope.(offset: Int) -> HttpResponseData,
        trophies: MockRequestHandleScope.() -> HttpResponseData = { ok("""{"trophyTitles":[]}""") },
        gallery: MockRequestHandleScope.(cursor: String?) -> HttpResponseData = { ok("""{"ugcDocument":[]}""") },
    ): PlayStationSource {
        val store = InMemoryCredentialStore()
        TypedCredentials(store, Platform.PLAYSTATION, PsnCredentials.serializer()).write(
            PsnCredentials(
                accessToken = "access",
                accessTokenExpiresAt = now.plus(Duration.ofHours(1)).epochSecond,
                refreshToken = "refresh",
                refreshTokenExpiresAt = now.plus(Duration.ofDays(60)).epochSecond,
                onlineId = "kratos",
            ),
        )
        val engine = MockEngine { request ->
            requests += request
            val path = request.url.encodedPath
            when {
                path.endsWith("/gamelist/v2/users/me/titles") -> titles(request.url.parameters["offset"]!!.toInt())
                path.endsWith("/trophyTitles") -> trophies()
                path.contains("cloudMediaGallery") -> gallery(request.url.parameters["cursorMark"])
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }
        return PlayStationSource.create(HttpClientFactory.create(engine), store, clock)
    }

    private fun title(id: String, duration: String?, category: String = "ps5_native_game", concept: Long? = null) =
        """{"titleId":"$id","name":"Game $id","category":"$category","playDuration":${duration?.let { "\"$it\"" } ?: "null"}""" +
            (concept?.let { ""","concept":{"id":$it}""" } ?: "") + "}"

    private fun page(vararg titles: String, nextOffset: Int? = null) =
        """{"titles":[${titles.joinToString()}]${nextOffset?.let { ""","nextOffset":$it""" } ?: ""}}"""

    private fun titleOffsets() =
        requests.filter { it.url.encodedPath.endsWith("/titles") }.map { it.url.parameters["offset"] }

    @Test
    fun `a page without nextOffset is the last one`() = runTest {
        val games = source(titles = { ok(page(title("A", "PT1H"), title("B", "PT2H"))) }).fetchLibrary()
        assertEquals(listOf("0"), titleOffsets())
        assertEquals(setOf("Game A", "Game B"), games.map { it.title }.toSet())
    }

    @Test
    fun `nextOffset that does not move forward stops paging instead of looping`() = runTest {
        source(titles = { ok(page(title("A", "PT1H"), nextOffset = 0)) }).fetchLibrary()
        assertEquals(listOf("0"), titleOffsets())
    }

    @Test
    fun `an empty page stops paging even if nextOffset is set`() = runTest {
        source(
            titles = { offset ->
                if (offset == 0) ok(page(title("A", "PT1H"), nextOffset = 1)) else ok(page(nextOffset = 2))
            },
        ).fetchLibrary()
        assertEquals(listOf("0", "1"), titleOffsets())
    }

    @Test
    fun `PT0S and missing durations are zero playtime, the game is still listed`() = runTest {
        val games = source(
            titles = { ok(page(title("PS4", "PT0S", category = "ps4_game"), title("NONE", null), title("BAD", "garbage"))) },
        ).fetchLibrary()
        assertEquals(3, games.size)
        games.forEach { assertEquals(it.title, Duration.ZERO, it.playtime) }
        assertEquals(setOf("PS4"), games.first { it.title == "Game PS4" }.devices)
    }

    @Test
    fun `PS4 and PS5 versions with one concept are summed into one entry`() = runTest {
        val games = source(
            titles = {
                ok(
                    page(
                        title("CUSA1", "PT10H", category = "ps4_game", concept = 42),
                        title("PPSA1", "PT1H30M", category = "ps5_native_game", concept = 42),
                    ),
                )
            },
        ).fetchLibrary()
        val game = games.single()
        assertEquals("concept:42", game.id.externalId)
        assertEquals(Duration.ofMinutes(11 * 60 + 30), game.playtime)
        assertEquals(setOf("PS4", "PS5"), game.devices)
    }

    @Test
    fun `trophy service failure does not break the library`() = runTest {
        val games = source(
            titles = { ok(page(title("A", "PT1H"))) },
            trophies = { respond("oops", HttpStatusCode.InternalServerError, json) },
        ).fetchLibrary()
        assertNull(games.single().achievementProgress)
    }

    @Test
    fun `rate limiting on the title list surfaces Retry-After`() = runTest {
        val src = source(
            titles = {
                respond(
                    content = "",
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.RetryAfter to listOf("30"), HttpHeaders.ContentType to listOf("application/json")),
                )
            },
        )
        try {
            src.fetchLibrary()
            fail("Expected RateLimited")
        } catch (e: PlatformException.RateLimited) {
            assertEquals(Duration.ofSeconds(30), e.retryAfter)
        }
    }

    @Test
    fun `gallery stops when the cursor repeats`() = runTest {
        val shots = source(
            titles = { ok(page()) },
            gallery = { ok("""{"ugcDocument":[{"id":"1","ugcType":1,"screenshotUrl":"https://s/1.jpg"}],"nextCursorMark":"same"}""") },
        ).fetchScreenshots()
        val calls = requests.count { it.url.encodedPath.contains("cloudMediaGallery") }
        assertEquals("first page + one page with cursor 'same'", 2, calls)
        assertTrue(shots.all { it.fullUrl == "https://s/1.jpg" })
    }

    @Test
    fun `gallery skips items without any image url and marks videos`() = runTest {
        val shots = source(
            titles = { ok(page()) },
            gallery = {
                ok(
                    """{"ugcDocument":[
                    {"id":"1","ugcType":1},
                    {"id":"2","ugcType":2,"downloadUrl":"https://s/2.mp4","sceTitleId":"PPSA1"}
                    ]}""",
                )
            },
        ).fetchScreenshots()
        val video = shots.single()
        assertEquals("psn-2", video.id)
        assertTrue(video.isVideo)
        assertEquals("PPSA1", video.gameId?.externalId)
    }
}
