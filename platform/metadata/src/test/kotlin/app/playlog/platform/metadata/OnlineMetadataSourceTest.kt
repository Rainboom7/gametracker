package app.playlog.platform.metadata

import app.playlog.core.data.MetadataQuery
import app.playlog.core.model.CriticScore
import app.playlog.core.model.UserReviews
import app.playlog.core.network.HttpClientFactory
import app.playlog.core.testing.FixedClock
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration

class OnlineMetadataSourceTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val clock = FixedClock()

    private fun fixture(name: String) = javaClass.getResource("/fixtures/metadata/$name")!!.readText()

    private fun source(hltbUp: Boolean = true, steamUp: Boolean = true): OnlineMetadataSource {
        val engine = MockEngine { request ->
            val body = when {
                request.url.host == "hltb.test" && !hltbUp -> null
                request.url.host == "store.test" && !steamUp -> null
                request.url.encodedPath == "/api/search/init" -> fixture("hltb_init.json")
                request.url.encodedPath == "/api/search" -> fixture("hltb_search.json")
                request.url.encodedPath == "/api/appdetails" ->
                    if (request.url.parameters["appids"] == "1245620") fixture("steam_appdetails.json") else fixture("steam_appdetails_fail.json")
                request.url.encodedPath.startsWith("/appreviews/") -> fixture("steam_appreviews.json")
                else -> null
            }
            if (body == null) {
                respond("down", HttpStatusCode.ServiceUnavailable)
            } else {
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val client = HttpClientFactory.create(engine)
        return OnlineMetadataSource(
            HowLongToBeatClient(client, baseUrl = "https://hltb.test"),
            SteamStoreClient(client, baseUrl = "https://store.test"),
            clock,
        )
    }

    private val eldenRing = MetadataQuery(key = "elden ring", title = "ELDEN RING", steamAppId = 1245620)

    @Test
    fun `merges HowLongToBeat with Steam store facts`() = runTest {
        val m = source().fetch(eldenRing)

        assertEquals("elden ring", m.key)
        assertEquals(clock.now(), m.fetchedAt)
        assertEquals(Duration.ofHours(59), m.howLongToBeat?.mainStory)
        assertEquals(CriticScore(94, "https://www.metacritic.com/game/pc/elden-ring?ftag=MCD-06-10aaa1f"), m.criticScore)
        assertEquals(UserReviews(93, 1000, "Very Positive"), m.userReviews)
        assertEquals("FromSoftware, Inc.", m.developer)
        assertEquals(2022, m.releaseYear)
        assertEquals(listOf("Action", "RPG"), m.genres)
    }

    @Test
    fun `console-only titles skip the Steam store and take facts from HowLongToBeat`() = runTest {
        val m = source().fetch(eldenRing.copy(steamAppId = null))

        assertNull(m.criticScore)
        assertNull(m.userReviews)
        assertEquals("FromSoftware", m.developer)
        assertEquals(2022, m.releaseYear)
    }

    @Test
    fun `one service down still yields a record`() = runTest {
        val m = source(hltbUp = false).fetch(eldenRing)

        assertNull(m.howLongToBeat)
        assertEquals(94, m.criticScore?.score)
    }

    @Test
    fun `everything down throws instead of caching an empty record`() = runTest {
        try {
            source(hltbUp = false, steamUp = false).fetch(eldenRing)
            fail("expected an exception")
        } catch (e: MetadataException) {
            assertTrue(e.message!!.contains("HowLongToBeat") || e.message!!.contains("Steam"))
        }
    }

    @Test
    fun `file store round-trips and drops corrupt files`() = runTest {
        val store = FileMetadataStore(temp.root)
        val m = source().fetch(eldenRing)

        store.put(m)
        assertEquals(m, store.get("elden ring"))
        assertNull(store.get("unknown"))

        temp.root.listFiles()!!.single { it.extension == "json" }.writeText("{not json")
        assertNull(store.get("elden ring"))
    }
}
