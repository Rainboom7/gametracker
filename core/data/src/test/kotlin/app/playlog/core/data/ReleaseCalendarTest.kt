package app.playlog.core.data

import app.playlog.core.model.Platform
import app.playlog.core.model.UpcomingGame
import app.playlog.core.model.WishlistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReleaseCalendarTest {
    private val today = LocalDate.parse("2026-10-21")
    private val zone = ZoneOffset.UTC

    private fun game(id: String, date: LocalDate?, vararg tags: String, text: String? = null) =
        UpcomingGame(Platform.STEAM, id, "Game $id", "h", "s", date, text, tags.toList())

    private val store = listOf(
        game("1", today.plusDays(2), "Roguelike", "Action"),
        game("2", today.plusDays(2), "Sports"),
        game("3", today.plusDays(30), "Roguelike"),
        game("4", null, "Puzzle", text = "Q1 2027"),
        game("5", today.minusDays(1), "Roguelike"),
    )
    private val taste = mapOf("Roguelike" to 0.8, "Action" to 0.4, "Puzzle" to 0.2)
    private val wishlist = listOf(
        WishlistItem(
            Platform.NINTENDO, "70010", "Switch Game", "h", "s", Instant.EPOCH,
            comingSoon = true, releaseDate = Instant.parse("2026-11-05T00:00:00Z"),
        ),
        WishlistItem(Platform.STEAM, "2", "Game 2", "h", "s", Instant.EPOCH, comingSoon = true),
    )

    @Test
    fun `days are sorted, past releases dropped, wishlist marked and merged in`() {
        val calendar = ReleaseCalendar.build(store, wishlist, taste, today, ReleaseFilter.ALL, zone)

        assertEquals(listOf(today.plusDays(2), today.plusDays(15), today.plusDays(30)), calendar.days.map { it.date })
        assertEquals(listOf("1", "2"), calendar.days.first().games.map { it.game.storeId })
        assertTrue(calendar.days.first().games[1].wishlisted)
        assertEquals(listOf("70010"), calendar.days[1].games.map { it.game.storeId })
        assertEquals("Q1 2027", calendar.undated.single().game.dateText)
        assertEquals("1", calendar.highlight?.game?.storeId)
        assertEquals(listOf("Roguelike", "Action"), calendar.days.first().games[0].sharedTags)
    }

    @Test
    fun `for you keeps strong matches and the wishlist, wishlist filter only the wishlist`() {
        val forYou = ReleaseCalendar.build(store, wishlist, taste, today, ReleaseFilter.FOR_YOU, zone)
        assertEquals(setOf("1", "2", "3", "70010"), forYou.days.flatMap { it.games }.map { it.game.storeId }.toSet())
        assertTrue(forYou.undated.isEmpty())

        val wished = ReleaseCalendar.build(store, wishlist, taste, today, ReleaseFilter.WISHLIST, zone)
        assertEquals(setOf("2", "70010"), wished.days.flatMap { it.games }.map { it.game.storeId }.toSet())
    }

    @Test
    fun `wishlisting a game keeps its place in the list`() {
        val before = ReleaseCalendar.build(store, emptyList(), taste, today, ReleaseFilter.ALL, zone)
        val after = ReleaseCalendar.build(store, wishlist, taste, today, ReleaseFilter.ALL, zone)

        val ids = { c: ReleaseCalendar -> c.days.first { it.date == today.plusDays(2) }.games.map { it.game.storeId } }
        assertEquals(ids(before), ids(after))
    }
}
