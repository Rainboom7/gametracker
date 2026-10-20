package app.playlog.core.model

import java.time.Duration
import java.time.Instant

/** Stable identity of a game inside one platform: Steam appid, PSN titleId / concept id, Nintendo titleId. */
data class GameId(val platform: Platform, val externalId: String) {
    /** Compact form for navigation arguments and database keys, e.g. `STEAM:570`. */
    fun encode(): String = "${platform.name}:$externalId"

    companion object {
        fun decode(value: String): GameId {
            val separator = value.indexOf(':')
            require(separator > 0) { "Malformed game id: $value" }
            return GameId(Platform.valueOf(value.substring(0, separator)), value.substring(separator + 1))
        }
    }
}

data class GameArtwork(
    /** Portrait box art (≈ 2:3). */
    val coverUrl: String? = null,
    /** Wide key art / banner (≈ 16:9 or wider). */
    val heroUrl: String? = null,
    /** Small square icon. */
    val iconUrl: String? = null,
    /** Fallback when [coverUrl] or [heroUrl] fail to load (Steam `header.jpg`, 460×215). */
    val bannerUrl: String? = null,
)

data class Game(
    val id: GameId,
    val title: String,
    val artwork: GameArtwork = GameArtwork(),
    /** Total time played as reported by the platform. */
    val playtime: Duration = Duration.ZERO,
    /** Time played in the last two weeks, when the platform reports it (Steam). */
    val recentPlaytime: Duration? = null,
    val firstPlayedAt: Instant? = null,
    val lastPlayedAt: Instant? = null,
    /** Number of launches / sessions, when known (PlayStation). */
    val playCount: Int? = null,
    /** Achievements / trophies earned, 0..100, when known. */
    val achievementProgress: Int? = null,
    /** Hardware the game was played on, e.g. "PS5", "Switch", "Windows". */
    val devices: Set<String> = emptySet(),
    val isFavorite: Boolean = false,
    /** Set by the user, never by sync. */
    val status: GameStatus = GameStatus.NONE,
    val isHidden: Boolean = false,
)
