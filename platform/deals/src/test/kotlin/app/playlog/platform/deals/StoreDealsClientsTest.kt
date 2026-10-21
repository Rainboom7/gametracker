package app.playlog.platform.deals

import app.playlog.core.model.Platform
import app.playlog.core.network.HttpClientFactory
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StoreDealsClientsTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/deals/$name")!!.readText()

    private val requests = mutableListOf<HttpRequestData>()

    private fun client(route: (HttpRequestData) -> String?) = HttpClientFactory.create(
        MockEngine { request ->
            requests += request
            respondBody(route(request))
        },
    )

    private fun MockRequestHandleScope.respondBody(body: String?): HttpResponseData =
        if (body == null) respond("nope", HttpStatusCode.NotFound) else respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun `steam lists discounted apps with regional prices`() = runTest {
        val store = SteamStoreDeals(
            client { r -> if (r.url.encodedPath == "/search/results/") fixture("steam_search.json") else fixture("steam_prices.json") },
            baseUrl = "https://store.test",
        )

        val page = store.page("TR", 0)

        assertEquals(listOf("Dead Cells"), page.deals.map { it.title })
        assertEquals("12,00 TL", page.deals.single().priceText)
        assertEquals("30,00 TL", page.deals.single().originalPriceText)
        assertEquals("https://store.steampowered.com/app/588650", page.deals.single().storeUrl)
        assertFalse(page.hasMore)
        assertEquals("588650,570,1000", requests.last().url.parameters["appids"])
        assertEquals("TR", requests.first().url.parameters["cc"])
    }

    @Test
    fun `playstation reads the persisted query and skips PS Plus-only and full price items`() = runTest {
        val store = PlayStationStoreDeals(client { fixture("psn_graphql.json") }, graphQlUrl = "https://gql.test/op", storeUrl = "https://ps.test")

        val page = store.page("TR", 0)

        val astro = page.deals.single()
        assertEquals(Platform.PLAYSTATION, astro.platform)
        assertEquals("Astro Bot", astro.title)
        assertEquals(40, astro.discountPercent)
        assertEquals("1 799,00 TL", astro.priceText)
        assertEquals("https://image.api.playstation.com/astro_wide.jpg", astro.headerUrl)
        assertEquals(listOf("https://image.api.playstation.com/astro_s1.jpg"), astro.screenshots)
        assertEquals("https://ps.test/en-tr/product/EP9000-PPSA01284_00-ASTROBOT", astro.storeUrl)
        assertEquals(Instant.ofEpochMilli(1767225600000), astro.endsAt)
        assertTrue(page.hasMore)
        assertEquals("en-TR", requests.single().headers["x-psn-store-locale-override"])
    }

    @Test
    fun `playstation falls back to the server rendered page when the query hash is stale`() = runTest {
        val store = PlayStationStoreDeals(
            client { r -> if (r.url.host == "gql.test") """{"errors":[{"message":"PersistedQueryNotFound"}]}""" else fixture("psn_category.html") },
            graphQlUrl = "https://gql.test/op",
            storeUrl = "https://ps.test",
        )

        val page = store.page("US", 0)

        assertEquals(listOf("Hades"), page.deals.map { it.title })
        assertEquals("https://ps.test/en-us/category/${PlayStationStoreDeals.DEALS_CATEGORY}/1", requests.last().url.toString())
    }

    @Test
    fun `nintendo europe uses the eShop price service of the user's country`() = runTest {
        val store = NintendoStoreDeals(
            client { r -> if (r.url.host == "noe.test") fixture("noe_search.json") else fixture("nintendo_prices.json") },
            europeSearchUrl = "https://noe.test",
            priceUrl = "https://price.test/v1/price",
        )

        val page = store.page("PL", 0)

        val hk = page.deals.single()
        assertEquals("Hollow Knight", hk.title)
        assertEquals(50, hk.discountPercent)
        assertEquals("32,00 zł", hk.priceText)
        assertEquals("64,00 zł", hk.originalPriceText)
        assertEquals("https://fs-prod-cdn.nintendo-europe.com/hk.jpg", hk.headerUrl)
        assertEquals("https://ec.nintendo.com/apps/70010000003207/PL", hk.storeUrl)
        assertEquals(Instant.parse("2026-10-29T22:59:59Z"), hk.endsAt)
        assertTrue(page.hasMore)
        assertEquals("/en/select", requests.first().url.encodedPath)
        assertEquals("PL", requests.last().url.parameters["country"])
    }

    @Test
    fun `nintendo america reads the Algolia catalogue`() = runTest {
        val store = NintendoStoreDeals(
            client { r -> if (r.url.host == "algolia.test") fixture("noa_algolia.json") else fixture("nintendo_prices_us.json") },
            algoliaUrl = "https://algolia.test",
            priceUrl = "https://price.test/v1/price",
        )

        val page = store.page("US", 0)

        assertEquals("Celeste", page.deals.single().title)
        assertEquals(75, page.deals.single().discountPercent)
        assertEquals("/1/indexes/store_game_en_us/query", requests.first().url.encodedPath)
        assertTrue(page.deals.single().headerUrl.startsWith("https://assets.nintendo.com/"))
    }

    @Test
    fun `steam reads search rows with user tags and reviews`() = runTest {
        val store = SteamStoreDeals(
            client { r ->
                when (r.url.encodedPath) {
                    "/search/results/" -> fixture("steam_search_rows.json")
                    "/tagdata/populartags/english" -> fixture("steam_tags.json")
                    else -> fixture("steam_prices.json")
                }
            },
            baseUrl = "https://store.test",
        )

        val page = store.page("TR", 0)

        val deadCells = page.deals.single()
        assertEquals(listOf("Roguelike", "Action Roguelike", "Crafting"), deadCells.tags)
        assertEquals(96, deadCells.reviewPercent)
        assertEquals(12.0, deadCells.priceValue!!, 0.001)
        assertTrue(page.hasMore)
        assertEquals("588650,1000", requests.first { it.url.encodedPath == "/api/appdetails" }.url.parameters["appids"])
        assertEquals(1, requests.count { it.url.encodedPath == "/search/results/" })
    }

    @Test
    fun `steam quotes name and price wishlisted apps in one call`() = runTest {
        val store = SteamStoreDeals(client { fixture("steam_getitems.json") }, apiUrl = "https://api.test")

        val quotes = store.quotes("TR", listOf("588650", "1000", "2000"))

        assertEquals("12,00 TL", quotes.getValue("588650").price?.priceText)
        assertEquals(60, quotes.getValue("588650").price?.discountPercent)
        assertEquals(Instant.ofEpochSecond(1767225600), quotes.getValue("588650").price?.endsAt)
        assertEquals(0, quotes.getValue("1000").price?.discountPercent)
        assertEquals("Coming Soon", quotes.getValue("2000").title)
        assertEquals(null, quotes.getValue("2000").price)
        assertTrue(requests.single().url.parameters["input_json"]!!.contains("\"country_code\":\"TR\""))
    }

    @Test
    fun `playstation keeps listings branded NONE and tries the next hash on a rotation`() = runTest {
        val store = PlayStationStoreDeals(
            client { r ->
                val hash = r.url.parameters["extensions"].orEmpty()
                val rotated = hash.contains(PlayStationStoreDeals.CATEGORY_GRID_HASHES.first())
                if (rotated) """{"errors":[{"message":"PersistedQueryNotFound"}]}""" else fixture("psn_graphql.json")
            },
            graphQlUrl = "https://gql.test/op",
            storeUrl = "https://ps.test",
        )

        assertEquals(listOf("Astro Bot"), store.page("TR", 0).deals.map { it.title })
        assertEquals(1799.0, store.page("TR", 0).deals.single().priceValue!!, 0.001)
        // The working hash is remembered: the second page asks once.
        assertEquals(3, requests.size)
    }

    @Test
    fun `nintendo quotes full price and sale prices`() = runTest {
        val store = NintendoStoreDeals(client { fixture("nintendo_prices.json") }, priceUrl = "https://price.test/v1/price")

        val quotes = store.quotes("PL", listOf("70010000003207", "70010000000001"))

        assertEquals(50, quotes.getValue("70010000003207").price?.discountPercent)
        assertEquals("100,00 zł", quotes.getValue("70010000000001").price?.priceText)
        assertEquals(0, quotes.getValue("70010000000001").price?.discountPercent)
    }

    @Test
    fun `nintendo search keeps games that are not on sale`() = runTest {
        val store = NintendoStoreDeals(
            client { r -> if (r.url.host == "noe.test") fixture("noe_search.json") else fixture("nintendo_prices.json") },
            europeSearchUrl = "https://noe.test",
            priceUrl = "https://price.test/v1/price",
        )

        val results = store.search("PL", "knight")

        assertEquals(listOf("Hollow Knight", "Not Discounted Here"), results.map { it.title })
        assertEquals("title:\"knight\"", requests.first().url.parameters["q"])
        assertEquals("type:GAME", requests.first().url.parameters["fq"])
    }

    @Test
    fun `cheapshark finds the all-time low of a Steam game`() = runTest {
        val history = CheapSharkPriceHistory(
            client { r -> if (r.url.parameters["steamAppID"] != null) fixture("cheapshark_lookup.json") else fixture("cheapshark_game.json") },
            baseUrl = "https://cs.test",
        )

        val low = history.lowest(Platform.STEAM, "588650")!!

        assertEquals("$4.94", low.priceText)
        assertEquals(Instant.ofEpochSecond(1700000000), low.at)
        assertEquals(null, history.lowest(Platform.NINTENDO, "1"))
        history.lowest(Platform.STEAM, "588650")
        assertEquals("cached", 2, requests.size)
    }

    @Test
    fun `price text parses every store locale`() {
        assertEquals(1799.0, PriceText.value("1 799,00 TL")!!, 0.001)
        assertEquals(19.99, PriceText.value("$19.99")!!, 0.001)
        assertEquals(1999.0, PriceText.value("₹1,999")!!, 0.001)
        assertEquals(1299.0, PriceText.value("1.299,00 zł")!!, 0.001)
        assertEquals(2750.0, PriceText.value("¥2,750")!!, 0.001)
        assertEquals(null, PriceText.value("Free"))
    }

    @Test
    fun `npId region helper is covered by the PlayStation module`() {
        // Region detection lives next to each account (platform modules); see PsnApi.regionFromNpId.
        assertTrue(StoreRegions.PLAYSTATION.containsKey("TR"))
        assertFalse(StoreRegions.PLAYSTATION.containsKey("RU"))
    }
}
