package app.playlog.platform.deals

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
import java.time.LocalDate

class SteamUpcomingTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/deals/$name")!!.readText()

    private val requests = mutableListOf<HttpRequestData>()

    private val client = HttpClientFactory.create(
        MockEngine { request ->
            requests += request
            val body = when (request.url.encodedPath) {
                "/search/results/" ->
                    if (request.url.parameters["start"] == "0") fixture("steam_upcoming_rows.json") else """{"success":1,"results_html":""}"""
                "/tagdata/populartags/english" -> fixture("steam_tags.json")
                "/IStoreBrowseService/GetItems/v1/" -> fixture("steam_getitems_upcoming.json")
                else -> null
            }
            if (body == null) {
                respond("nope", HttpStatusCode.NotFound)
            } else {
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        },
    )

    private val upcoming = SteamUpcoming(
        client,
        SteamStoreDeals(client, baseUrl = "https://store.test", apiUrl = "https://api.test"),
        baseUrl = "https://store.test",
        pages = 2,
    )

    @Test
    fun `release column gives the day, GetItems fills missing ones, windows stay windows`() = runTest {
        val games = upcoming.upcoming("RU").associateBy { it.storeId }

        assertEquals(LocalDate.of(2026, 10, 24), games.getValue("900001").date)
        assertEquals(listOf("Roguelike", "Action", "Crafting"), games.getValue("900001").tags)
        assertEquals("Iron Tide & Co", games.getValue("900002").title)
        assertNull(games.getValue("900002").date)
        assertEquals("Q1 2027", games.getValue("900002").dateText)
        assertEquals(LocalDate.of(2026, 11, 23), games.getValue("900004").date)
        assertEquals(LocalDate.of(2026, 11, 3), games.getValue("900003").date)
        assertEquals("barely tagged and adult rows are dropped", setOf("900001", "900002", "900003", "900004"), games.keys)
        val search = requests.first { it.url.encodedPath == "/search/results/" }
        assertEquals("popularcomingsoon", search.url.parameters["filter"])
        assertEquals("RU", search.url.parameters["cc"])
    }

    @Test
    fun `day formats parse, windows do not`() {
        assertEquals(LocalDate.of(2026, 9, 5), SteamUpcoming.parseDate("5 Sept, 2026"))
        assertEquals(LocalDate.of(2026, 12, 31), SteamUpcoming.parseDate("Dec 31, 2026"))
        assertNull(SteamUpcoming.parseDate("Q1 2027"))
        assertNull(SteamUpcoming.parseDate("October 2026"))
        assertNull(SteamUpcoming.parseDate("Coming soon"))
    }
}
