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
    fun `npId region helper is covered by the PlayStation module`() {
        // Region detection lives next to each account (platform modules); see PsnApi.regionFromNpId.
        assertTrue(StoreRegions.PLAYSTATION.containsKey("TR"))
        assertFalse(StoreRegions.PLAYSTATION.containsKey("RU"))
    }
}
