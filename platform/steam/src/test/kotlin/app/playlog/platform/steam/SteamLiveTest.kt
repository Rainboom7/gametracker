package app.playlog.platform.steam

import app.playlog.core.network.HttpClientFactory
import app.playlog.core.testing.requireEnv
import app.playlog.platform.api.InMemoryCredentialStore
import app.playlog.platform.api.LinkFieldType
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/** Real Steam Web API. Run with `./gradlew :platform:steam:liveTest` and STEAM_API_KEY + STEAM_ID set. */
class SteamLiveTest {
    @Test
    fun `links and reads the real library`() = runTest(timeout = 2.minutes) {
        val apiKey = requireEnv("STEAM_API_KEY")
        val steamId = requireEnv("STEAM_ID")
        val source = SteamSource(HttpClientFactory.create(CIO.create()), InMemoryCredentialStore())

        source.linker.complete(
            source.linker.start(),
            mapOf(LinkFieldType.STEAM_API_KEY to apiKey, LinkFieldType.STEAM_PROFILE to steamId),
        )
        val games = source.fetchLibrary()

        assertTrue("library is empty: profile private or API changed", games.isNotEmpty())
        assertTrue("no game has playtime", games.any { it.playtime.toMillis() > 0 })
        assertTrue("no game has a title", games.all { it.title.isNotBlank() })
    }
}
