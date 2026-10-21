package app.playlog.platform.deals

import app.playlog.core.data.FinderQuery
import app.playlog.core.model.PlayFeature
import app.playlog.core.network.HttpClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamTagSearchTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/deals/$name")!!.readText()

    private val requests = mutableListOf<HttpRequestData>()

    private val client = HttpClientFactory.create(
        MockEngine { request ->
            requests += request
            val body = when (request.url.encodedPath) {
                "/search/results/" -> fixture("steam_search_rows.json")
                "/tagdata/populartags/english" -> fixture("steam_tags.json")
                "/api/appdetails" -> fixture("steam_prices.json")
                "/api/storesearch/" -> fixture("storesearch_celeste.json")
                "/api.php" -> fixture("spy_504230.json")
                else -> null
            }
            if (body == null) {
                respond("nope", HttpStatusCode.NotFound)
            } else {
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        },
    )

    private val search = SteamTagSearch(
        client,
        SteamStoreDeals(client, baseUrl = "https://store.test"),
        SteamSpyClient(client, baseUrl = "https://spy.test", minIntervalMillis = 0),
        baseUrl = "https://store.test",
    )

    @Test
    fun `tags go by id, play modes as categories, unknown tags are dropped`() = runTest {
        val picks = search.find(FinderQuery("TR", tags = listOf("Roguelike", "No Such Tag"), features = setOf(PlayFeature.SPLIT_SCREEN_COOP), limit = 10))

        val searchRequest = requests.first { it.url.encodedPath == "/search/results/" }
        assertEquals("1716", searchRequest.url.parameters["tags"])
        assertEquals("39", searchRequest.url.parameters["category3"])
        assertNull(searchRequest.url.parameters["specials"])
        assertEquals("Dead Cells", picks.first().title)
        assertEquals("12,00 TL", picks.first().priceText)
        assertEquals(listOf("Roguelike", "Action Roguelike", "Crafting"), picks.first().tags)
    }

    @Test
    fun `price and sale words map to store filters`() = runTest {
        search.find(FinderQuery("TR", tags = listOf("Action"), free = true, onSale = true, limit = 10))

        val searchRequest = requests.first { it.url.encodedPath == "/search/results/" }
        assertEquals("free", searchRequest.url.parameters["maxprice"])
        assertEquals("1", searchRequest.url.parameters["specials"])
    }

    @Test
    fun `games like a title borrow its strongest SteamSpy tags`() = runTest {
        assertEquals(
            listOf("Precision Platformer", "Platformer", "Difficult", "Pixel Graphics", "2D Platformer"),
            search.tagsOf("Celeste"),
        )
    }

    @Test
    fun `falls back to the json search mode when the html mode fails`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val flaky = HttpClientFactory.create(
            MockEngine { request ->
                seen += request
                when {
                    request.url.encodedPath == "/search/results/" && request.url.parameters["infinite"] == "1" ->
                        respond("<html>busy</html>", HttpStatusCode.OK)
                    request.url.encodedPath == "/search/results/" ->
                        respond(fixture("steam_search.json"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    request.url.encodedPath == "/tagdata/populartags/english" ->
                        respond(fixture("steam_tags.json"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    request.url.encodedPath == "/api/appdetails" ->
                        respond(fixture("steam_prices.json"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    else -> respond("nope", HttpStatusCode.NotFound)
                }
            },
        )
        val fallback = SteamTagSearch(flaky, SteamStoreDeals(flaky, baseUrl = "https://store.test"), SteamSpyClient(flaky), baseUrl = "https://store.test")

        val picks = fallback.find(FinderQuery("TR", tags = listOf("Roguelike"), limit = 10))

        assertEquals("Dead Cells", picks.first().title)
        val json = seen.last { it.url.encodedPath == "/search/results/" }
        assertEquals("1", json.url.parameters["json"])
        assertEquals("1716", json.url.parameters["tags"])
    }
}
