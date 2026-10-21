package app.playlog.core.model

/** Why a game from the user's own library is suggested on the Discover tab. */
enum class PickReason {
    /** Played in the last weeks and not finished. */
    CONTINUE,

    /** Most achievements / trophies already earned. */
    ALMOST_DONE,

    /** Many hours in, untouched for months. */
    RETURN_TO,

    /** Owned but barely launched. */
    BACKLOG,
}

data class Pick(
    val entry: LibraryEntry,
    val reason: PickReason,
    /** Higher is a better suggestion right now; only used for ordering. */
    val score: Double,
    /** Days since the game was last played, null when the platform never reported it. */
    val daysSinceLastPlayed: Long?,
)

data class DiscoverShelf(val reason: PickReason, val picks: List<Pick>)

enum class MilestoneKind {
    /** Hours in a single title. */
    MARATHON,

    /** Hours across the whole library. */
    VETERAN,

    /** Titles in the library. */
    COLLECTOR,

    /** Titles marked completed. */
    FINISHER,

    /** Titles with every achievement / trophy. */
    PERFECTIONIST,

    /** Titles played on two or more platforms. */
    MULTIPLATFORM,

    /** Longest run of consecutive days with any play in the last year. */
    STREAK,
}

/**
 * A personal badge with levels. [level] is how many [thresholds] [value] has reached (0 = locked).
 */
data class Milestone(
    val kind: MilestoneKind,
    val value: Long,
    val thresholds: List<Long>,
) {
    val level: Int get() = thresholds.count { value >= it }
    val maxLevel: Int get() = thresholds.size
    val isMaxed: Boolean get() = level == maxLevel

    /** Threshold of the next level, null when maxed. */
    val nextTarget: Long? get() = thresholds.getOrNull(level)

    /** Progress from the current level to the next one, 0..1 (1 when maxed). */
    val progressToNext: Float
        get() {
            val next = nextTarget ?: return 1f
            val previous = thresholds.getOrNull(level - 1) ?: 0L
            return ((value - previous).toFloat() / (next - previous)).coerceIn(0f, 1f)
        }
}

/** "N years ago this week you first played X." */
data class Anniversary(val entry: LibraryEntry, val years: Int)

data class DiscoverSummary(
    /** The single best suggestion for tonight. */
    val topPick: Pick?,
    /** Non-empty shelves in display order. */
    val shelves: List<DiscoverShelf>,
    val milestones: List<Milestone>,
    val anniversaries: List<Anniversary>,
    /** Titles in the backlog pool (owned, barely played, not completed or dropped). */
    val backlogCount: Int,
)
