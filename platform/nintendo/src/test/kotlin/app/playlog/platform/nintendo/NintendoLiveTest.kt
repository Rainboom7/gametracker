package app.playlog.platform.nintendo

import app.playlog.core.model.Platform
import app.playlog.core.network.HttpClientFactory
import app.playlog.core.testing.requireEnv
import app.playlog.platform.api.InMemoryCredentialStore
import app.playlog.platform.api.TypedCredentials
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Unofficial Nintendo API. The link flow needs a browser, so the test starts from a saved
 * session token (valid ~2 years). Run with `./gradlew :platform:nintendo:liveTest` and NSO_SESSION_TOKEN set.
 */
class NintendoLiveTest {
    @Test
    fun `session token refreshes and reads play history`() = runTest(timeout = 2.minutes) {
        val sessionToken = requireEnv("NSO_SESSION_TOKEN")
        val store = InMemoryCredentialStore()
        TypedCredentials(store, Platform.NINTENDO, NintendoCredentials.serializer())
            .write(NintendoCredentials(sessionToken = sessionToken))
        val source = NintendoSource.create(HttpClientFactory.create(CIO.create()), store)

        val games = source.fetchLibrary()

        assertTrue("play history is empty or API changed", games.isNotEmpty())
        assertTrue("no title has playtime", games.any { it.playtime.toMillis() > 0 })
    }
}
