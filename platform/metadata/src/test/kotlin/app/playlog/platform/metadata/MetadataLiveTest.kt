package app.playlog.platform.metadata

import app.playlog.core.data.MetadataQuery
import app.playlog.core.network.HttpClientFactory
import app.playlog.core.testing.FixedClock
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/** Real HowLongToBeat and Steam store. No secrets needed: `./gradlew :platform:metadata:liveTest`. */
class MetadataLiveTest {
    @Test
    fun `finds Elden Ring everywhere`() = runTest(timeout = 2.minutes) {
        val source = OnlineMetadataSource(HttpClientFactory.create(CIO.create()), FixedClock())

        val m = source.fetch(MetadataQuery(key = "elden ring", title = "ELDEN RING", steamAppId = 1245620))

        assertNotNull("HowLongToBeat lookup failed: endpoint or body format changed", m.howLongToBeat?.mainStory)
        assertNotNull("Steam store has no Metacritic score", m.criticScore)
        assertNotNull("Steam reviews missing", m.userReviews)
    }
}
