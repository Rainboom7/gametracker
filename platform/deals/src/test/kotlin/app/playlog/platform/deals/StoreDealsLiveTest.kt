package app.playlog.platform.deals

import app.playlog.core.network.HttpClientFactory
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Real stores, no secrets: `./gradlew :platform:deals:liveTest`. A failure here means a store
 * changed its undocumented format (PlayStation rotates its GraphQL hash, for example).
 */
class StoreDealsLiveTest {
    private val client = HttpClientFactory.create(CIO.create())

    @Test
    fun `steam turkey`() = runTest(timeout = 2.minutes) {
        assertTrue(SteamStoreDeals(client).page("TR", 0).deals.isNotEmpty())
    }

    @Test
    fun `playstation turkey`() = runTest(timeout = 2.minutes) {
        assertTrue(PlayStationStoreDeals(client).page("TR", 0).deals.isNotEmpty())
    }

    @Test
    fun `nintendo poland`() = runTest(timeout = 2.minutes) {
        assertTrue(NintendoStoreDeals(client).page("PL", 0).deals.isNotEmpty())
    }

    @Test
    fun `nintendo united states`() = runTest(timeout = 2.minutes) {
        assertTrue(NintendoStoreDeals(client).page("US", 0).deals.isNotEmpty())
    }
}
