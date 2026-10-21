package app.playlog.core.model

import java.time.Instant

/** Where a free game is claimed. */
enum class GiveawayStore { EPIC, STEAM, GOG, PLAYSTATION, XBOX, NINTENDO, ITCH, OTHER }

/** A game that is free to keep for a limited time (or will be, when [upcoming]). */
data class Giveaway(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val url: String,
    val store: GiveawayStore,
    /** "PC, Steam" as the source writes it. */
    val platformsText: String = "",
    /** Usual price, e.g. "$19.99". */
    val worth: String? = null,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
    val description: String? = null,
    val upcoming: Boolean = false,
    /** Already in the user's library. */
    val owned: Boolean = false,
)

sealed interface GiveawaysState {
    data object Loading : GiveawaysState

    data class Ready(val items: List<Giveaway>, val fetchedAt: Instant) : GiveawaysState

    data class Failed(val stale: List<Giveaway>, val message: String?) : GiveawaysState
}

val GiveawaysState.items: List<Giveaway>
    get() = when (this) {
        is GiveawaysState.Ready -> items
        is GiveawaysState.Failed -> stale
        GiveawaysState.Loading -> emptyList()
    }
