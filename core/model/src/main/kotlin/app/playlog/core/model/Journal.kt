package app.playlog.core.model

import java.time.Instant

/** A milestone with its unlock history; [isRecent] marks a level reached in the last days. */
data class Badge(
    val milestone: Milestone,
    /** When the current level was reached; null for levels that predate the app tracking them. */
    val unlockedAt: Instant?,
    val isRecent: Boolean,
) {
    val kind: MilestoneKind get() = milestone.kind
    val level: Int get() = milestone.level
}

/** What the app remembers about a badge between runs. */
data class BadgeRecord(
    val level: Int,
    val unlockedAt: Instant?,
    /** Highest level already shown in a notification or on the badges screen. */
    val seenLevel: Int,
)

/** The user's own verdict on a game: a 1..10 rating and free-form notes, kept only on the device. */
data class GameNote(
    val rating: Int? = null,
    val text: String = "",
    val updatedAt: Instant,
) {
    val isEmpty: Boolean get() = rating == null && text.isBlank()

    companion object {
        val RATING_RANGE = 1..10
    }
}
