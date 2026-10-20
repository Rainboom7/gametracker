package app.playlog.core.model

import java.time.Duration
import java.time.Instant

/**
 * One title in the library, merged across platforms by [TitleKey]: "Hades" owned on Steam and
 * Switch is a single entry whose playtime is the sum of both.
 */
data class LibraryEntry(
    val key: String,
    val games: List<Game>,
) {
    init {
        require(games.isNotEmpty()) { "LibraryEntry needs at least one game" }
    }

    /** The member with the most playtime; its id is what navigation uses. */
    val primary: Game get() = games.maxWith(compareBy<Game> { it.playtime }.thenBy { PLATFORM_ART_PRIORITY.indexOf(it.id.platform) * -1 })

    val title: String get() = primary.title
    val platforms: Set<Platform> get() = games.mapTo(sortedSetOf()) { it.id.platform }
    val totalPlaytime: Duration get() = games.fold(Duration.ZERO) { acc, g -> acc + g.playtime }
    val lastPlayedAt: Instant? get() = games.mapNotNull { it.lastPlayedAt }.maxOrNull()
    val firstPlayedAt: Instant? get() = games.mapNotNull { it.firstPlayedAt }.minOrNull()
    val isFavorite: Boolean get() = games.any { it.isFavorite }
    val status: GameStatus get() = games.firstOrNull { it.status != GameStatus.NONE }?.status ?: GameStatus.NONE

    /** All trophies / achievements earned but not marked completed: the UI suggests marking it. */
    val suggestCompleted: Boolean get() = achievementProgress == 100 && status != GameStatus.COMPLETED

    /** Achievement progress of the platform with the most playtime that reports it. */
    val achievementProgress: Int?
        get() = games.sortedByDescending { it.playtime }.firstNotNullOfOrNull { it.achievementProgress }

    /** Best portrait cover: Steam library art, then PSN, then Nintendo's square icon. */
    val coverUrl: String? get() = byArtPriority().firstNotNullOfOrNull { it.artwork.coverUrl }
    val heroUrl: String? get() = byArtPriority().firstNotNullOfOrNull { it.artwork.heroUrl ?: it.artwork.bannerUrl }
    val fallbackCoverUrl: String? get() = byArtPriority().firstNotNullOfOrNull { it.artwork.bannerUrl ?: it.artwork.iconUrl }

    fun playtimeOn(platform: Platform): Duration =
        games.filter { it.id.platform == platform }.fold(Duration.ZERO) { acc, g -> acc + g.playtime }

    private fun byArtPriority() = games.sortedBy { PLATFORM_ART_PRIORITY.indexOf(it.id.platform) }

    companion object {
        private val PLATFORM_ART_PRIORITY = listOf(Platform.STEAM, Platform.PLAYSTATION, Platform.NINTENDO)

        /** Groups per-platform games into merged entries. Hidden games are dropped. */
        fun group(games: List<Game>): List<LibraryEntry> =
            games.asSequence()
                .filterNot { it.isHidden }
                .groupBy { TitleKey.of(it.title).ifEmpty { it.id.encode() } }
                .map { (key, members) -> LibraryEntry(key, members) }
    }
}
