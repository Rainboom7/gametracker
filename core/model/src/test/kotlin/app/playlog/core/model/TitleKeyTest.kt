package app.playlog.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TitleKeyTest {
    @Test
    fun sameGameAcrossStoresMatches() {
        val key = TitleKey.of("DOOM Eternal")
        assertEquals(key, TitleKey.of("DOOM Eternal™"))
        assertEquals(key, TitleKey.of("Doom Eternal – PS5"))
        assertEquals(key, TitleKey.of("DOOM Eternal (PS4 & PS5)"))
        assertEquals(key, TitleKey.of("DOOM Eternal Deluxe Edition"))
    }

    @Test
    fun punctuationApostrophesAndDiacriticsAreIgnored() {
        assertEquals(TitleKey.of("Marvel's Spider-Man 2"), TitleKey.of("Marvel’s Spider-Man 2™"))
        assertEquals(TitleKey.of("Pokémon Scarlet"), TitleKey.of("Pokemon Scarlet"))
        assertEquals("the witcher 3 wild hunt", TitleKey.of("The Witcher® 3: Wild Hunt – Game of the Year Edition"))
    }

    @Test
    fun sequelsStayDistinct() {
        assertNotEquals(TitleKey.of("Hades"), TitleKey.of("Hades II"))
        assertNotEquals(TitleKey.of("Portal"), TitleKey.of("Portal 2"))
    }
}
