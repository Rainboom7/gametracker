package app.playlog.platform.deals

import app.playlog.core.data.Ownership
import app.playlog.core.data.TasteSeed
import app.playlog.core.network.HttpClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OnlineDealsSourceTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/deals/$name")!!.readText()

    private val requests = mutableListOf<String>()
    private val tagStore = InMemoryTagStore()

    private fun source(dealsUp: Boolean = true): OnlineDealsSource {
        val engine = MockEngine { request ->
            val url = request.url
            requests += "${url.host}${url.encodedPath}?${url.parameters["appid"] ?: url.parameters["appids"] ?: url.parameters["term"] ?: ""}"
            val body = when {
                !dealsUp && (url.host == "cheapshark.test" || url.encodedPath == "/api/featuredcategories") -> null
                url.host == "cheapshark.test" -> if (url.parameters["pageNumber"] == "0") fixture("cheapshark_deals.json") else "[]"
                url.encodedPath == "/api/featuredcategories" -> fixture("featured.json")
                url.encodedPath == "/api/storesearch/" ->
                    if (url.parameters["term"] == "Celeste") fixture("storesearch_celeste.json") else """{"total":0,"items":[]}"""
                url.host == "spy.test" -> javaClass.getResource("/fixtures/deals/spy_${url.parameters["appid"]}.json")?.readText() ?: fixture("spy_empty.json")
                url.encodedPath == "/api/appdetails" ->
                    javaClass.getResource("/fixtures/deals/appdetails_${url.parameters["appids"]}.json")?.readText() ?: fixture("appdetails_not_sold.json")
                else -> null
            }
            if (body == null) {
                respond("down", HttpStatusCode.ServiceUnavailable)
            } else {
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val client = HttpClientFactory.create(engine)
        return OnlineDealsSource(
            CheapSharkClient(client, baseUrl = "https://cheapshark.test"),
            SteamShopClient(client, countryCode = "RU", language = "russian", baseUrl = "https://store.test"),
            SteamSpyClient(client, baseUrl = "https://spy.test", minIntervalMillis = 0),
            tagStore,
        )
    }

    private val seeds = listOf(
        TasteSeed("Hades", steamAppId = 1145360, weight = 3.0),
        TasteSeed("Celeste", steamAppId = null, weight = 2.0),
        TasteSeed("The Legend of Zelda: Tears of the Kingdom", steamAppId = null, weight = 2.0),
    )
    private val owned = Ownership(setOf(1145360L), setOf("hades", "celeste", "the legend of zelda tears of the kingdom"))

    @Test
    fun `recommends similar unowned games on sale with regional prices and screenshots`() = runTest {
        val picks = source().recommend(seeds, owned).picks

        assertEquals(listOf("Dead Cells", "Hollow Knight"), picks.map { it.title })
        val deadCells = picks.first()
        assertEquals("207 руб.", deadCells.priceText)
        assertEquals("519 руб.", deadCells.originalPriceText)
        assertEquals(60, deadCells.discountPercent)
        assertEquals(96, deadCells.reviewPercent)
        assertEquals(listOf("https://cdn.test/dc/s1.jpg", "https://cdn.test/dc/s2.jpg"), deadCells.screenshots)
        assertEquals("Рогалайк-метроидвания & экшен.", deadCells.shortDescription)
        assertEquals("Hades", deadCells.becauseOf.first())
        assertTrue(deadCells.sharedTags.contains("Roguelite"))
        assertEquals("Эпическое приключение.", picks[1].shortDescription)
    }

    @Test
    fun `never spends requests on owned, badly reviewed or full price games`() = runTest {
        source().recommend(seeds, owned).picks

        val spied = requests.filter { it.startsWith("spy.test") }.map { it.substringAfter('?') }.toSet()
        assertEquals(setOf("1145360", "504230", "588650", "367520", "2000"), spied)
    }

    @Test
    fun `console titles are matched to Steam by exact name and cached`() = runTest {
        source().recommend(seeds, owned).picks
        assertEquals(504230L, tagStore.appId("celeste"))
        assertEquals(TagStore.NOT_ON_STEAM, tagStore.appId("the legend of zelda tears of the kingdom"))

        requests.clear()
        source().recommend(seeds, owned).picks
        assertTrue("cached lookups repeat", requests.none { it.contains("storesearch") || it.startsWith("spy.test") })
    }

    @Test
    fun `throws when no deals feed is reachable`() = runTest {
        try {
            source(dealsUp = false).recommend(seeds, owned).picks
            fail("expected DealsException")
        } catch (e: DealsException) {
            assertTrue(e.message!!.contains("feed"))
        }
    }
}
