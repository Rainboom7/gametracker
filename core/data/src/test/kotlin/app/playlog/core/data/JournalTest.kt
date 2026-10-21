package app.playlog.core.data

import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.MilestoneKind
import app.playlog.core.model.Platform
import app.playlog.core.testing.FixedClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class JournalTest {
    private val clock = FixedClock()
    private val library = InMemoryLibraryStore()
    private val libraryRepository = DefaultLibraryRepository(library)
    private val notes = DefaultGameNotesRepository(InMemoryGameNotesStore(), clock)
    private val badges = DefaultBadgesRepository(
        DefaultDiscoverRepository(library, libraryRepository, clock),
        DefaultScreenshotRepository(library),
        notes,
        InMemoryBadgeStore(),
        clock,
    )

    private suspend fun addGames(count: Int, hours: Long = 0) = library.upsertGames(
        Platform.STEAM,
        (1..count).map { Game(GameId(Platform.STEAM, "$it"), "Game $it", playtime = Duration.ofHours(hours)) },
    )

    @Test
    fun `first run remembers levels silently, later level-ups are announced once`() = runTest {
        addGames(10, hours = 12)
        assertTrue(badges.takeUnlocks().isEmpty())

        addGames(10, hours = 60)
        val unlocked = badges.takeUnlocks()
        assertEquals(listOf(MilestoneKind.MARATHON, MilestoneKind.VETERAN), unlocked.map { it.kind })
        assertEquals(clock.instant, unlocked.first().unlockedAt)
        assertTrue(badges.takeUnlocks().isEmpty())
    }

    @Test
    fun `recent flag lasts a week and badges seen on screen are not announced again`() = runTest {
        addGames(1)
        badges.takeUnlocks()
        addGames(1, hours = 11)

        assertTrue(badges.observeBadges().first().first { it.kind == MilestoneKind.MARATHON }.isRecent)
        badges.markSeen()
        assertTrue(badges.takeUnlocks().isEmpty())

        clock.instant = clock.instant.plus(Duration.ofDays(8))
        val marathon = badges.observeBadges().first().first { it.kind == MilestoneKind.MARATHON }
        assertEquals(1, marathon.level)
        assertFalse(marathon.isRecent)
    }

    @Test
    fun `ratings count toward the critic badge and empty notes are dropped`() = runTest {
        (1..5).forEach { notes.setRating("k$it", 8) }
        assertEquals(1, badges.observeBadges().first().first { it.kind == MilestoneKind.CRITIC }.level)

        notes.setText("k1", "  great  ")
        assertEquals("great", notes.observe("k1").first()?.text)
        notes.setText("k1", "")
        notes.setRating("k1", null)
        assertNull(notes.observe("k1").first())
    }
}
