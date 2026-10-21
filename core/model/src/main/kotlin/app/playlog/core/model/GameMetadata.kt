package app.playlog.core.model

import java.time.Duration
import java.time.Instant

/**
 * Facts about a title from outside the user's platforms (HowLongToBeat, store pages). Keyed by
 * [LibraryEntry.key], so one record serves the merged entry on every platform.
 */
data class GameMetadata(
    val key: String,
    val fetchedAt: Instant,
    val howLongToBeat: HowLongToBeat? = null,
    /** Metacritic critic score, 0..100. */
    val criticScore: CriticScore? = null,
    val userReviews: UserReviews? = null,
    val developer: String? = null,
    val releaseYear: Int? = null,
    val genres: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = howLongToBeat == null && criticScore == null && userReviews == null && developer == null && releaseYear == null
}

data class HowLongToBeat(
    val id: Long,
    val title: String,
    val mainStory: Duration? = null,
    val mainExtra: Duration? = null,
    val completionist: Duration? = null,
    /** HowLongToBeat players' rating, 0..100. */
    val userScore: Int? = null,
) {
    val url: String get() = "https://howlongtobeat.com/game/$id"
}

data class CriticScore(val score: Int, val url: String? = null)

/** Steam store user reviews. */
data class UserReviews(
    val percentPositive: Int,
    val total: Int,
    /** Steam's own label, e.g. "Very Positive". */
    val summary: String? = null,
)
