package app.playlog.core.testing

import app.playlog.core.data.LibraryStore
import app.playlog.core.model.DailyPlaytime
import app.playlog.core.model.Game
import app.playlog.core.model.GameArtwork
import app.playlog.core.model.GameId
import app.playlog.core.model.GameStatus
import app.playlog.core.model.Platform
import app.playlog.core.model.PlaytimeSnapshot
import app.playlog.core.model.Screenshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Behaviour every [LibraryStore] must share. Subclass it per implementation:
 * the in-memory fake runs it on the JVM, Room runs it under Robolectric.
 */
@Suppress("TooManyFunctions") // one function per contract case
abstract class LibraryStoreContract {
    protected abstract fun createStore(): LibraryStore

    /** Releases resources, e.g. closes the database. */
    protected open fun close() = Unit

    private val store by lazy { createStore() }

    @After
    fun tearDownStore() = close()

    private val hades = Game(
        id = GameId(Platform.STEAM, "1145360"),
        title = "Hades",
        artwork = GameArtwork(
            coverUrl = "https://cdn/hades/library_600x900.jpg",
            heroUrl = "https://cdn/hades/library_hero.jpg",
            iconUrl = "https://cdn/hades/icon.jpg",
            bannerUrl = "https://cdn/hades/header.jpg",
        ),
        playtime = Duration.ofMinutes(4_321),
        recentPlaytime = Duration.ofMinutes(90),
        firstPlayedAt = Instant.parse("2021-01-02T03:04:05Z"),
        lastPlayedAt = Instant.parse("2025-10-19T21:00:00Z"),
        playCount = 12,
        achievementProgress = 64,
        devices = setOf("Windows", "Steam Deck"),
    )
    private val zelda = Game(id = GameId(Platform.NINTENDO, "0100F2C0115B6000"), title = "Zelda: TotK", playtime = Duration.ofHours(120))
    private val astro = Game(id = GameId(Platform.PLAYSTATION, "concept:10010882"), title = "ASTRO BOT", playtime = Duration.ofHours(14))

    private suspend fun games() = store.observeGames().first().associateBy { it.id }

    @Test
    fun `games round-trip with every field`() = runTest {
        store.upsertGames(Platform.STEAM, listOf(hades))
        assertEquals(hades, games()[hades.id])
    }

    @Test
    fun `games without optional fields round-trip`() = runTest {
        store.upsertGames(Platform.NINTENDO, listOf(zelda))
        assertEquals(zelda, games()[zelda.id])
    }

    @Test
    fun `sync keeps favorite, hidden and status of existing games`() = runTest {
        store.upsertGames(Platform.STEAM, listOf(hades))
        store.setFavorite(hades.id, true)
        store.setHidden(hades.id, true)
        store.setStatus(hades.id, GameStatus.COMPLETED)

        store.upsertGames(Platform.STEAM, listOf(hades.copy(playtime = Duration.ofMinutes(5_000))))

        val stored = games().getValue(hades.id)
        assertEquals(Duration.ofMinutes(5_000), stored.playtime)
        assertTrue(stored.isFavorite)
        assertTrue(stored.isHidden)
        assertEquals(GameStatus.COMPLETED, stored.status)
    }

    @Test
    fun `every status round-trips`() = runTest {
        store.upsertGames(Platform.STEAM, listOf(hades))
        for (status in GameStatus.entries) {
            store.setStatus(hades.id, status)
            assertEquals(status, games().getValue(hades.id).status)
        }
    }

    @Test
    fun `games missing from a later sync are kept`() = runTest {
        val other = Game(id = GameId(Platform.STEAM, "570"), title = "Dota 2")
        store.upsertGames(Platform.STEAM, listOf(hades, other))
        store.upsertGames(Platform.STEAM, listOf(hades))
        assertEquals(setOf(hades.id, other.id), games().keys)
    }

    @Test
    fun `flags can be cleared and unknown ids are ignored`() = runTest {
        store.upsertGames(Platform.STEAM, listOf(hades))
        store.setFavorite(hades.id, true)
        store.setFavorite(hades.id, false)
        store.setHidden(GameId(Platform.STEAM, "does-not-exist"), true)
        assertEquals(false, games().getValue(hades.id).isFavorite)
        assertEquals(setOf(hades.id), games().keys)
    }

    @Test
    fun `latestTotals takes the newest snapshot per game of one platform`() = runTest {
        val t0 = Instant.parse("2025-10-01T00:00:00Z")
        store.insertSnapshots(
            listOf(
                PlaytimeSnapshot(hades.id, t0, Duration.ofHours(10)),
                PlaytimeSnapshot(hades.id, t0.plusSeconds(3_600), Duration.ofHours(11)),
                PlaytimeSnapshot(zelda.id, t0, Duration.ofHours(100)),
            ),
        )
        store.insertSnapshots(emptyList())
        assertEquals(mapOf(hades.id to Duration.ofHours(11)), store.latestTotals(Platform.STEAM))
        assertEquals(emptyMap<GameId, Duration>(), store.latestTotals(Platform.PLAYSTATION))
    }

    private val day = LocalDate.of(2025, 10, 19)

    private suspend fun dailyOn(date: LocalDate) =
        store.observeDaily(date, date).first().associate { it.gameId to it.playtime }

    @Test
    fun `derived daily playtime accumulates across calls`() = runTest {
        store.addDerivedDaily(listOf(DailyPlaytime(hades.id, day, Duration.ofMinutes(30))))
        store.addDerivedDaily(listOf(DailyPlaytime(hades.id, day, Duration.ofMinutes(45))))
        assertEquals(Duration.ofMinutes(75), dailyOn(day)[hades.id])
    }

    @Test
    fun `derived entries for the same game and day in one call add up`() = runTest {
        store.addDerivedDaily(
            listOf(
                DailyPlaytime(hades.id, day, Duration.ofMinutes(30)),
                DailyPlaytime(hades.id, day, Duration.ofMinutes(45)),
            ),
        )
        assertEquals(Duration.ofMinutes(75), dailyOn(day)[hades.id])
    }

    @Test
    fun `exact daily playtime wins over derived, before and after`() = runTest {
        store.addDerivedDaily(listOf(DailyPlaytime(zelda.id, day, Duration.ofMinutes(10))))
        store.replaceExactDaily(listOf(DailyPlaytime(zelda.id, day, Duration.ofMinutes(95))))
        store.addDerivedDaily(listOf(DailyPlaytime(zelda.id, day, Duration.ofMinutes(10))))
        assertEquals(Duration.ofMinutes(95), dailyOn(day)[zelda.id])

        store.replaceExactDaily(listOf(DailyPlaytime(zelda.id, day, Duration.ofMinutes(80))))
        assertEquals(Duration.ofMinutes(80), dailyOn(day)[zelda.id])
    }

    @Test
    fun `observeDaily range is inclusive on both ends`() = runTest {
        val days = (0L..4L).map { day.plusDays(it) }
        store.replaceExactDaily(days.map { DailyPlaytime(zelda.id, it, Duration.ofMinutes(1)) })
        val dates = store.observeDaily(days[1], days[3]).first().map { it.date }.toSet()
        assertEquals(setOf(days[1], days[2], days[3]), dates)
    }

    private val shot = Screenshot(
        id = "psn-1",
        gameId = astro.id,
        platform = Platform.PLAYSTATION,
        fullUrl = "https://psn/1.jpg",
        thumbnailUrl = "https://psn/1_s.jpg",
        takenAt = Instant.parse("2025-10-18T10:00:00Z"),
        width = 3840,
        height = 2160,
        isVideo = false,
    )

    @Test
    fun `screenshots upsert by id, round-trip and delete`() = runTest {
        val imported = Screenshot(id = "import-1", gameId = null, platform = Platform.NINTENDO, fullUrl = "content://media/1")
        store.upsertScreenshots(listOf(shot, imported))
        store.upsertScreenshots(listOf(shot.copy(thumbnailUrl = "https://psn/1_m.jpg")))

        val stored = store.observeScreenshots().first().associateBy { it.id }
        assertEquals(shot.copy(thumbnailUrl = "https://psn/1_m.jpg"), stored["psn-1"])
        assertEquals(imported, stored["import-1"])

        store.deleteScreenshot("psn-1")
        assertEquals(setOf("import-1"), store.observeScreenshots().first().map { it.id }.toSet())
    }

    @Test
    fun `deletePlatform removes only that platform`() = runTest {
        store.upsertGames(Platform.STEAM, listOf(hades))
        store.upsertGames(Platform.PLAYSTATION, listOf(astro))
        store.insertSnapshots(listOf(PlaytimeSnapshot(astro.id, Instant.EPOCH, Duration.ofHours(1))))
        store.replaceExactDaily(listOf(DailyPlaytime(astro.id, day, Duration.ofHours(1)), DailyPlaytime(hades.id, day, Duration.ofHours(2))))
        store.upsertScreenshots(listOf(shot))

        store.deletePlatform(Platform.PLAYSTATION)

        assertEquals(setOf(hades.id), games().keys)
        assertEquals(emptyMap<GameId, Duration>(), store.latestTotals(Platform.PLAYSTATION))
        assertEquals(setOf(hades.id), dailyOn(day).keys)
        assertEquals(emptyList<Screenshot>(), store.observeScreenshots().first())
    }
}
