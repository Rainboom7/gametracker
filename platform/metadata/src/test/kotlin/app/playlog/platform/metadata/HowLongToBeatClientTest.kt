package app.playlog.platform.metadata

import app.playlog.core.network.HttpClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class HowLongToBeatClientTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun fixture(name: String) = javaClass.getResource("/fixtures/metadata/$name")!!.readText()

    /** [routes] maps "METHOD path" to (status, body). */
    private fun client(routes: Map<String, Pair<HttpStatusCode, String>>): HowLongToBeatClient {
        val engine = MockEngine { request ->
            requests += request
            val (status, body) = routes["${request.method.value} ${request.url.encodedPath}"] ?: (HttpStatusCode.NotFound to "")
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return HowLongToBeatClient(HttpClientFactory.create(engine), baseUrl = "https://hltb.test", nowMillis = { 42 })
    }

    @Test
    fun `uses the init token and picks the exact title over a more popular spin-off`() = runTest {
        val hltb = client(
            mapOf(
                "GET /api/search/init" to (HttpStatusCode.OK to fixture("hltb_init.json")),
                "POST /api/search" to (HttpStatusCode.OK to fixture("hltb_search.json")),
            ),
        )

        val match = hltb.find("ELDEN RING™")!!

        assertEquals(68151000L, match.game.id)
        assertEquals(Duration.ofHours(59), match.game.mainStory)
        assertEquals(Duration.ofHours(100), match.game.mainExtra)
        assertEquals(Duration.ofHours(134), match.game.completionist)
        assertEquals(92, match.game.userScore)
        assertEquals(2022, match.releaseYear)
        assertEquals("https://howlongtobeat.com/game/68151000", match.game.url)

        val search = requests.single { it.method == HttpMethod.Post }
        assertEquals("abc123token", search.headers["x-auth-token"])
        val body = (search.body as TextContent).text
        assertTrue(body, body.contains("\"searchTerms\":[\"elden\",\"ring\"]"))
        assertEquals("42", requests.first().url.parameters["t"])
    }

    @Test
    fun `falls back to the endpoint written in the app bundle`() = runTest {
        val hltb = client(
            mapOf(
                "GET /" to (HttpStatusCode.OK to fixture("hltb_home.html")),
                "GET /_next/static/chunks/pages/_app-5e3b7c1a9d2f4e6b.js" to (HttpStatusCode.OK to fixture("hltb_app.js")),
                "POST /api/seek/4b4cb53ad9a1f2" to (HttpStatusCode.OK to fixture("hltb_search.json")),
            ),
        )

        assertEquals(68151000L, hltb.find("Elden Ring")?.game?.id)
        assertNull(requests.last().headers["x-auth-token"])
    }

    @Test
    fun `rediscovers once when the cached endpoint starts answering 404`() = runTest {
        var searches = 0
        val engine = MockEngine { request ->
            requests += request
            when ("${request.method.value} ${request.url.encodedPath}") {
                "GET /api/search/init" -> respond(fixture("hltb_init.json"), HttpStatusCode.OK)
                "POST /api/search" -> if (searches++ == 1) {
                    respond("", HttpStatusCode.NotFound)
                } else {
                    respond(fixture("hltb_search.json"), HttpStatusCode.OK)
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val hltb = HowLongToBeatClient(HttpClientFactory.create(engine), baseUrl = "https://hltb.test")

        hltb.find("Elden Ring")
        assertEquals(68151000L, hltb.find("Elden Ring")?.game?.id)
        assertEquals(2, requests.count { it.url.encodedPath == "/api/search/init" })
    }

    @Test
    fun `no close title means no match`() {
        val results = listOf(dto("Hades II"), dto("Hades: Battle Out of Hell"))
        assertNull(HowLongToBeatClient.bestMatch("hollow knight", results))
        assertEquals("Hades II", HowLongToBeatClient.bestMatch("hades ii", results)?.name)
        // "hades" is a prefix of "hades ii" and close in length: accepted as a fuzzy match.
        assertEquals("Hades II", HowLongToBeatClient.bestMatch("hades", results)?.name)
    }

    @Test
    fun `reads concat and plus style endpoints from the bundle`() {
        assertEquals("/api/seek/abcdef", HowLongToBeatClient.searchPathFromScript("""fetch("/api/seek/".concat("abc").concat("def"),{})"""))
        assertEquals("/api/find/a1b2", HowLongToBeatClient.searchPathFromScript("""fetch("/api/find/" + "a1b2", {})"""))
        assertNull(HowLongToBeatClient.searchPathFromScript("nothing here"))
    }

    private fun dto(name: String) = HltbGameDto(id = name.length.toLong(), name = name, main = 3600)
}
