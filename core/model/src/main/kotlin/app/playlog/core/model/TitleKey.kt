package app.playlog.core.model

import java.text.Normalizer

/**
 * Normalizes game titles so the same game on several platforms (and trophy lists, which carry
 * no ids) can be matched: "DOOM Eternal™ – PS5" and "Doom Eternal" both become "doom eternal".
 */
object TitleKey {
    private val TRADEMARKS = Regex("[™®©℠]")
    private val PLATFORM_SUFFIX = Regex("\\s*[-–—:(\\[]\\s*(ps4|ps5|ps4\\s*&\\s*ps5|ps4\\s*and\\s*ps5|nintendo switch( 2)?|switch|pc|windows)\\s*[)\\]]?\\s*$")
    private val EDITION_SUFFIX = Regex(
        "\\s*[-–—:(\\[]?\\s*(game of the year|goty|definitive|deluxe|complete|ultimate|standard|digital deluxe|remastered|director'?s cut|anniversary|enhanced|gold|premium)( edition)?\\s*[)\\]]?\\s*$",
    )
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
    private val DIACRITICS = Regex("\\p{Mn}+")

    fun of(title: String): String {
        // Trademarks go first: NFKD would turn ™ into "TM".
        var s = Normalizer.normalize(title.replace(TRADEMARKS, ""), Normalizer.Form.NFKD).replace(DIACRITICS, "")
        s = s.lowercase().replace("'", "").replace("’", "").trim()
        // Suffixes can stack ("… Deluxe Edition (PS5)"), strip until stable.
        while (true) {
            val stripped = s.replace(PLATFORM_SUFFIX, "").replace(EDITION_SUFFIX, "").trim()
            if (stripped == s || stripped.isEmpty()) break
            s = stripped
        }
        return s.replace(NON_ALNUM, " ").trim()
    }
}
