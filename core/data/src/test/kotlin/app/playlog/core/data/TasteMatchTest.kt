package app.playlog.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteMatchTest {
    private val taste = mapOf("Roguelite" to 0.8, "Metroidvania" to 0.6)

    @Test
    fun `matching first tags score high, unrelated tags zero`() {
        val close = TasteMatch.score(taste, listOf("roguelite", "Metroidvania", "Pixel Graphics"))
        val far = TasteMatch.score(taste, listOf("Racing", "Sports"))
        assertTrue(close > 0.6f)
        assertEquals(0f, far)
    }

    @Test
    fun `earlier tags weigh more`() {
        assertTrue(TasteMatch.score(taste, listOf("Roguelite", "Racing")) > TasteMatch.score(taste, listOf("Racing", "Roguelite")))
    }

    @Test
    fun `no taste or no tags is no match`() {
        assertEquals(0f, TasteMatch.score(emptyMap(), listOf("Roguelite")))
        assertEquals(0f, TasteMatch.score(taste, emptyList()))
    }
}
