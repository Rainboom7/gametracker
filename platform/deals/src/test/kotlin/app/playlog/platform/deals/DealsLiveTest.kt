package app.playlog.platform.deals

import app.playlog.core.data.Ownership
import app.playlog.core.data.TasteSeed
import app.playlog.core.network.HttpClientFactory
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

/** Real CheapShark, Steam store and SteamSpy. No secrets: `./gradlew :platform:deals:liveTest`. */
class DealsLiveTest {
    @Test
    fun `finds deals for a roguelite fan`() = runTest(timeout = 5.minutes) {
        val source = OnlineDealsSource(HttpClientFactory.create(CIO.create()), InMemoryTagStore(), Locale("ru", "RU"))

        val picks = source.recommend(listOf(TasteSeed("Hades", 1145360, 3.0), TasteSeed("Celeste", null, 2.0)), Ownership(setOf(1145360), emptySet())).picks

        assertTrue("no picks: a feed or SteamSpy format changed", picks.isNotEmpty())
        assertTrue("no screenshots: appdetails format changed", picks.any { it.screenshots.isNotEmpty() })
    }
}
