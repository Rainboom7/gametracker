package app.playlog.feature.library

import app.playlog.core.model.LibraryEntry
import app.playlog.core.model.Platform
import app.playlog.core.model.TitleKey
import java.time.Duration
import java.time.Instant

enum class LibrarySort { LAST_PLAYED, PLAYTIME, TITLE, FIRST_PLAYED }

/** Search, platform filter and sort for the library grid. Pure, so it is unit-tested on the JVM. */
object LibraryFilter {
    fun apply(entries: List<LibraryEntry>, query: String, platform: Platform?, sort: LibrarySort): List<LibraryEntry> {
        val needle = TitleKey.of(query)
        val filtered = entries.filter { e ->
            (platform == null || platform in e.platforms) &&
                (needle.isEmpty() || TitleKey.of(e.title).contains(needle) || e.title.contains(query.trim(), ignoreCase = true))
        }
        val comparator: Comparator<LibraryEntry> = when (sort) {
            LibrarySort.LAST_PLAYED -> compareByDescending<LibraryEntry, Instant?>(nullsFirst()) { it.lastPlayedAt }
            LibrarySort.PLAYTIME -> compareByDescending { it.totalPlaytime }
            LibrarySort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            LibrarySort.FIRST_PLAYED -> compareByDescending<LibraryEntry, Instant?>(nullsFirst()) { it.firstPlayedAt }
        }
        return filtered.sortedWith(comparator.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    /** The most recently played title if it was played within [window] of [now]. */
    fun nowPlaying(entries: List<LibraryEntry>, now: Instant, window: Duration = Duration.ofHours(24)): LibraryEntry? =
        entries.filter { it.lastPlayedAt?.isAfter(now.minus(window)) == true }.maxByOrNull { it.lastPlayedAt!! }
}

private fun <T, K : Comparable<K>> compareByDescending(nulls: Comparator<K?>, selector: (T) -> K?): Comparator<T> =
    Comparator { a, b -> nulls.compare(selector(b), selector(a)) }

private fun <K : Comparable<K>> nullsFirst(): Comparator<K?> = Comparator { a, b ->
    when {
        a === b -> 0
        a == null -> -1
        b == null -> 1
        else -> a.compareTo(b)
    }
}
