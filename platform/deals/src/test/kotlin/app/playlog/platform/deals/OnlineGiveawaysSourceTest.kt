package app.playlog.platform.deals

import app.playlog.core.model.GiveawayStore
import app.playlog.core.network.HttpClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OnlineGiveawaysSourceTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/deals/$name")!!.readText()

    private fun source(epicUp: Boolean = true, gamerPowerUp: Boolean = true) = OnlineGiveawaysSource(
        HttpClientFactory.create(
            MockEngine { r ->
                val body = when {
                    r.url.host == "epic.test" && epicUp -> fixture("epic_free.json")
                    r.url.host == "gp.test" && gamerPowerUp -> fixture("gamerpower.json")
                    else -> null
                }
                if (body == null) {
                    respond("down", HttpStatusCode.ServiceUnavailable)
                } else {
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            },
        ),
        gamerPowerUrl = "https://gp.test",
        epicUrl = "https://epic.test",
    )

    @Test
    fun `merges Epic and GamerPower, Epic wins duplicates, expired and paid offers dropped`() = runTest {
        val items = source().fetch()

        assertEquals(listOf("Hollow Knight", "Next Week Game", "Celeste"), items.map { it.title })
        val hk = items[0]
        assertEquals("https://store.epicgames.com/p/hollow-knight-0a1b", hk.url)
        assertEquals("https://epic.test/hk_wide.jpg", hk.imageUrl)
        assertEquals("$14.99", hk.worth)
        assertEquals(Instant.parse("2026-10-23T15:00:00Z"), hk.endsAt)
        assertFalse(hk.upcoming)
        assertTrue(items[1].upcoming)
        assertEquals(GiveawayStore.STEAM, items[2].store)
        assertEquals(null, items[2].worth)
        assertEquals(null, items[2].endsAt)
    }

    @Test
    fun `one feed down still lists the other`() = runTest {
        assertEquals(listOf("Hollow Knight", "Celeste"), source(epicUp = false).fetch().map { it.title })
    }

    @Test(expected = DealsException::class)
    fun `throws when both feeds are down`() = runTest {
        source(epicUp = false, gamerPowerUp = false).fetch()
    }

    @Test
    fun `cleans GamerPower titles`() {
        assertEquals("Hades", OnlineGiveawaysSource.cleanTitle("Hades (Epic Games) Giveaway"))
        assertEquals("Celeste", OnlineGiveawaysSource.cleanTitle("Celeste Giveaway"))
        assertEquals("Giveaway", OnlineGiveawaysSource.cleanTitle("Giveaway"))
    }

    @Test
    fun `cleartext image links are upgraded to https`() {
        assertEquals("https://img.test/a.jpg", OnlineGiveawaysSource.secure("http://img.test/a.jpg"))
        assertEquals("https://img.test/b.jpg", OnlineGiveawaysSource.secure("https://img.test/b.jpg"))
    }
}
