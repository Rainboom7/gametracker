package app.playlog.platform.deals

import app.playlog.core.model.Platform
import app.playlog.core.network.HttpClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NintendoUpcomingTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/deals/$name")!!.readText()

    private val requests = mutableListOf<HttpRequestData>()

    private val upcoming = NintendoUpcoming(
        HttpClientFactory.create(
            MockEngine { request ->
                requests += request
                val body = if (request.url.host == "noe.test") fixture("noe_upcoming.json") else fixture("noa_upcoming.json")
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ),
        europeSearchUrl = "https://noe.test",
        algoliaUrl = "https://algolia.test",
    )

    @Test
    fun `europe keeps known publishers with their release day`() = runTest {
        val games = upcoming.upcoming("PL").associateBy { it.title }

        assertEquals(setOf("Star Hollow Odyssey", "Tactics Unbound"), games.keys)
        val star = games.getValue("Star Hollow Odyssey")
        assertEquals(Platform.NINTENDO, star.platform)
        assertEquals("70010000090001", star.storeId)
        assertEquals(LocalDate.of(2026, 11, 14), star.date)
        assertEquals("https://fs-prod-cdn.nintendo-europe.com/sho.jpg", star.headerUrl)
        assertEquals(listOf("Adventure", "Action"), star.tags)
        assertTrue(requests.single().url.parameters["fq"]!!.contains("dates_released_dts:[NOW TO"))
    }

    @Test
    fun `americas filters coming soon and drops unknown publishers`() = runTest {
        val games = upcoming.upcoming("US")

        val harbor = games.single()
        assertEquals("Harbor Lights", harbor.title)
        assertEquals(LocalDate.of(2026, 12, 5), harbor.date)
        assertTrue(harbor.headerUrl.endsWith("ncom/software/switch/harbor/hero"))
        assertEquals("algolia.test", requests.single().url.host)
    }

    @Test
    fun `publisher match ignores case and suffixes`() {
        assertTrue(NintendoUpcoming.isKnownPublisher("SEGA of America, Inc."))
        assertTrue(!NintendoUpcoming.isKnownPublisher("Quick Flip Media"))
    }
}
