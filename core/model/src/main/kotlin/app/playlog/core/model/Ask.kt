package app.playlog.core.model

/** How many play and where; the Steam finder maps these to store categories. */
enum class PlayFeature {
    SPLIT_SCREEN_COOP,
    SHARED_SCREEN,
    LOCAL_PVP,
    ONLINE_COOP,
    COOP,
    PVP,
    SINGLE_PLAYER,
    REMOTE_PLAY_TOGETHER,
    CONTROLLER,
}

/**
 * What a free-text request like "игра на 2 на 1 экране" asks for. Every field is optional: the
 * parser only fills what it recognized and keeps the rest in [leftover] for a plain title search.
 */
data class AskIntent(
    val text: String,
    /** Steam user tag names in English ("Local Co-Op", "Roguelike"), most important first. */
    val tags: List<String> = emptyList(),
    val features: Set<PlayFeature> = emptySet(),
    val players: Int? = null,
    val platforms: Set<Platform> = emptySet(),
    val onSale: Boolean = false,
    val free: Boolean = false,
    /** Only titles the user owns. */
    val ownedOnly: Boolean = false,
    /** Owned but barely played. */
    val unplayed: Boolean = false,
    /** "как Hades": find games like this title. */
    val similarTo: String? = null,
    /** Words that matched nothing; searched as a title when nothing else was recognized. */
    val leftover: String = "",
) {
    val understood: Boolean
        get() = tags.isNotEmpty() || features.isNotEmpty() || similarTo != null || ownedOnly || unplayed || free || onSale
}

/** One answer: owned titles that fit first, then store games, plus why each store game was picked. */
data class AskAnswer(
    val intent: AskIntent,
    val owned: List<LibraryEntry>,
    val store: List<DealPick>,
    /** Tags borrowed from [AskIntent.similarTo], shown as "like Hades: Roguelite, Action". */
    val similarTags: List<String> = emptyList(),
)
