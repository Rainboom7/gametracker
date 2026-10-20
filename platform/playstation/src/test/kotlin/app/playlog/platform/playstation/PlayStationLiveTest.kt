package app.playlog.platform.playstation

import app.playlog.core.network.HttpClientFactory
import app.playlog.core.testing.requireEnv
import app.playlog.platform.api.InMemoryCredentialStore
import app.playlog.platform.api.LinkFieldType
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/** Unofficial PSN API. Run with `./gradlew :platform:playstation:liveTest` and PSN_NPSSO set (expires in ~2 months). */
class PlayStationLiveTest {
    @Test
    fun `npsso links and reads played titles`() = runTest(timeout = 2.minutes) {
        val npsso = requireEnv("PSN_NPSSO")
        val source = PlayStationSource.create(HttpClientFactory.create(CIO.create()), InMemoryCredentialStore())

        source.linker.complete(source.linker.start(), mapOf(LinkFieldType.PSN_NPSSO to npsso))
        val games = source.fetchLibrary()

        assertTrue("played titles are empty: privacy settings or API changed", games.isNotEmpty())
        assertTrue("no title has playtime", games.any { it.playtime.toMillis() > 0 })
        assertTrue("no cover art", games.any { it.artwork.coverUrl != null })
    }
}
