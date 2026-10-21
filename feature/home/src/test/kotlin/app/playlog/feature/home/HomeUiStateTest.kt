package app.playlog.feature.home

import app.playlog.core.model.ActivityPeriod
import app.playlog.core.model.ActivitySummary
import app.playlog.core.model.DealPick
import app.playlog.core.model.DealsState
import app.playlog.core.model.DiscoverShelf
import app.playlog.core.model.DiscoverSummary
import app.playlog.core.model.Game
import app.playlog.core.model.GameId
import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Pick
import app.playlog.core.model.PickReason
import app.playlog.core.model.Platform
import app.playlog.core.model.Screenshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class HomeUiStateTest {
    private val now = Instant.parse("2026-10-20T20:00:00Z")
    private val day = LocalDate.parse("2026-10-20")

    private fun game(platform: Platform, id: String, title: String, hours: Long, daysAgo: Long?) = Game(
        GameId(platform, id),
        title,
        playtime = Duration.ofHours(hours),
        lastPlayedAt = daysAgo?.let { now.minusSeconds(it * 86_400) },
    )

    private val hadesSteam = game(Platform.STEAM, "1145360", "Hades", 40, 0)
    private val hadesSwitch = game(Platform.NINTENDO, "0100535012974000", "Hades", 10, 5)
    private val zelda = game(Platform.NINTENDO, "0100F2C0115B6000", "Zelda", 120, 2)
    private val astro = game(Platform.PLAYSTATION, "concept:1", "ASTRO BOT", 14, 10)
    private val unplayed = game(Platform.STEAM, "2", "Backlog", 0, null)
    private val library = LibraryEntry.group(listOf(hadesSteam, hadesSwitch, zelda, astro, unplayed))

    private val week = ActivitySummary(ActivityPeriod.WEEK, day.minusDays(6), day, Duration.ofHours(9), Duration.ZERO, emptyList(), emptyMap(), emptyList())

    private fun pick(title: String, reason: PickReason) = Pick(library.single { it.title == title }, reason, 1.0, null)

    private fun discover(top: Pick?, vararg shelf: Pick) =
        DiscoverSummary(top, listOf(DiscoverShelf(PickReason.CONTINUE, shelf.toList())), emptyList(), emptyList(), backlogCount = 7)

    private fun build(library: List<LibraryEntry> = this.library, discover: DiscoverSummary = discover(null), shots: List<Screenshot> = emptyList()) =
        HomeUiState.build(library, week, 3, discover, shots, DayPart.EVENING)

    @Test
    fun `hero is the last played title and the recent row follows newest first`() {
        val state = build() as HomeUiState.Content

        assertEquals("Hades", state.continuePlaying?.title)
        assertEquals(listOf("Zelda", "ASTRO BOT"), state.recent.map { it.title })
        assertEquals(4, state.gameCount)
        assertEquals(Duration.ofHours(184), state.totalPlaytime)
        assertEquals(7, state.backlogCount)
    }

    @Test
    fun `tonight's pick skips the title already in the hero`() {
        val state = build(discover = discover(pick("Hades", PickReason.CONTINUE), pick("Hades", PickReason.CONTINUE), pick("Zelda", PickReason.RETURN_TO)))

        assertEquals("Zelda", (state as HomeUiState.Content).topPick?.entry?.title)
    }

    @Test
    fun `screenshots are newest first and carry the game's title`() {
        val old = Screenshot("a", zelda.id, Platform.NINTENDO, "https://x/a.jpg", takenAt = now.minusSeconds(500))
        val fresh = Screenshot("b", hadesSteam.id, Platform.STEAM, "https://x/b.jpg", takenAt = now)
        val orphan = Screenshot("c", null, Platform.PLAYSTATION, "content://c", takenAt = now.minusSeconds(100))

        val shots = (build(shots = listOf(old, fresh, orphan)) as HomeUiState.Content).screenshots

        assertEquals(listOf(HomeShot(fresh, "Hades"), HomeShot(orphan, null), HomeShot(old, "Zelda")), shots)
    }

    @Test
    fun `deals come from fresh picks or the stale ones after a failure`() {
        val deal = DealPick(Platform.STEAM, "1", "Hollow Knight", "https://x/h.jpg", "https://x/app/1", discountPercent = 50, priceText = "250 ₽")
        fun deals(state: DealsState) = (HomeUiState.build(library, week, 3, discover(null), emptyList(), DayPart.EVENING, state) as HomeUiState.Content).deals

        assertEquals(listOf(deal), deals(DealsState.Ready(listOf(deal), now)))
        assertEquals(listOf(deal), deals(DealsState.Failed(listOf(deal))))
        assertEquals(emptyList<DealPick>(), deals(DealsState.NoTaste))
    }

    @Test
    fun `an empty library asks to connect`() {
        assertTrue(build(library = emptyList()) is HomeUiState.Empty)
    }

    @Test
    fun `greeting follows the time of day`() {
        assertEquals(DayPart.MORNING, HomeUiState.dayPart(LocalTime.of(7, 0)))
        assertEquals(DayPart.DAY, HomeUiState.dayPart(LocalTime.of(12, 0)))
        assertEquals(DayPart.EVENING, HomeUiState.dayPart(LocalTime.of(22, 59)))
        assertEquals(DayPart.NIGHT, HomeUiState.dayPart(LocalTime.of(2, 0)))
    }
}
