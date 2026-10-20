package app.playlog.core.data

import app.playlog.core.common.AppClock
import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.Platform
import app.playlog.core.model.Screenshot
import app.playlog.core.model.SyncFailure
import app.playlog.core.model.SyncState
import app.playlog.platform.api.PlatformException
import app.playlog.platform.api.SourceCapability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SyncEngineTest {
    private var now = Instant.parse("2025-10-20T20:00:00Z")
    private val clock = object : AppClock {
        override fun now() = now
        override fun zone(): ZoneId = ZoneOffset.UTC
    }
    private val library = InMemoryLibraryStore()
    private val accounts = InMemoryAccountStore()
    private val steam = FakeSource(Platform.STEAM, setOf(SourceCapability.TOTAL_PLAYTIME, SourceCapability.SCREENSHOTS))
    private val psn = FakeSource(Platform.PLAYSTATION)
    private val nintendo = FakeSource(Platform.NINTENDO, setOf(SourceCapability.TOTAL_PLAYTIME, SourceCapability.DAILY_PLAYTIME))
    private val engine = SyncEngine(setOf(steam, psn, nintendo), library, accounts, clock)

    private val portal = GameId(Platform.STEAM, "620")

    private fun steamGame(minutes: Long, lastPlayed: Instant? = null) =
        Game(portal, "Portal 2", playtime = Duration.ofMinutes(minutes), lastPlayedAt = lastPlayed)

    private suspend fun daily(from: String = "2025-01-01", to: String = "2025-12-31") =
        library.observeDaily(LocalDate.parse(from), LocalDate.parse(to)).first()

    @Test
    fun `first sync records a baseline without inventing activity`() = runTest {
        steam.library = listOf(steamGame(600))
        engine.syncAll()

        assertEquals(1, library.allSnapshots.size)
        assertTrue(daily().isEmpty())
        assertEquals(1, library.observeGames().first().size)
    }

    @Test
    fun `playtime growth is booked on the last played day`() = runTest {
        steam.library = listOf(steamGame(600))
        engine.syncAll()

        now = Instant.parse("2025-10-22T09:00:00Z")
        steam.library = listOf(steamGame(690, lastPlayed = Instant.parse("2025-10-21T22:00:00Z")))
        val report = engine.syncAll()

        assertEquals(listOf(DailyPlaytime(portal, LocalDate.parse("2025-10-21"), Duration.ofMinutes(90))), daily())
        assertEquals(Duration.ofMinutes(90), report.results.first { it.platform == Platform.STEAM }.playtimeAdded)
    }

    @Test
    fun `unchanged totals add no snapshots or activity`() = runTest {
        steam.library = listOf(steamGame(600))
        engine.syncAll()
        now = now.plusSeconds(3600)
        engine.syncAll()

        assertEquals(1, library.allSnapshots.size)
        assertTrue(daily().isEmpty())
    }

    @Test
    fun `exact daily data is stored and not double counted with deltas`() = runTest {
        val totk = GameId(Platform.NINTENDO, "0100F2C0115B6000")
        nintendo.library = listOf(Game(totk, "Zelda", playtime = Duration.ofMinutes(1000)))
        engine.syncAll()
        nintendo.library = listOf(Game(totk, "Zelda", playtime = Duration.ofMinutes(1095)))
        nintendo.daily = listOf(DailyPlaytime(totk, LocalDate.parse("2025-10-20"), Duration.ofMinutes(95)))
        engine.syncAll()

        assertEquals(listOf(DailyPlaytime(totk, LocalDate.parse("2025-10-20"), Duration.ofMinutes(95))), daily())
    }

    @Test
    fun `one failing platform does not block the others`() = runTest {
        steam.library = listOf(steamGame(600))
        psn.libraryError = PlatformException.AuthExpired(Platform.PLAYSTATION)

        val report = engine.syncAll()

        assertEquals(listOf(Platform.PLAYSTATION), report.needsRelink)
        val states = accounts.observeAccounts().first().associate { it.platform to it.syncState }
        assertEquals(SyncState.Idle, states[Platform.STEAM])
        assertEquals(SyncFailure.AUTH_EXPIRED, (states[Platform.PLAYSTATION] as SyncState.Failed).reason)
        assertEquals(1, library.observeGames().first().size)
    }

    @Test
    fun `screenshot failure is a warning, not a failed sync`() = runTest {
        steam.library = listOf(steamGame(600))
        steam.screenshotError = PlatformException.Protocol(Platform.STEAM, "boom")

        val result = engine.sync(Platform.STEAM)!!

        assertTrue(result.isSuccess)
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun `screenshots are stored`() = runTest {
        steam.screenshots = listOf(Screenshot("steam-1", portal, Platform.STEAM, "https://x/1.jpg"))
        engine.sync(Platform.STEAM)
        assertEquals(1, library.observeScreenshots().first().size)
    }

    @Test
    fun `unlinked platforms are skipped`() = runTest {
        psn.linked = false
        assertNull(engine.sync(Platform.PLAYSTATION))
        assertTrue(engine.syncAll().results.none { it.platform == Platform.PLAYSTATION })
    }

    @Test
    fun `user flags survive a resync`() = runTest {
        steam.library = listOf(steamGame(600))
        engine.syncAll()
        library.setFavorite(portal, true)
        steam.library = listOf(steamGame(700))
        engine.syncAll()

        assertTrue(library.observeGames().first().single().isFavorite)
    }
}
